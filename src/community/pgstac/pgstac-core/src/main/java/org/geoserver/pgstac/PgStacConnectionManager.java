/* (c) 2026 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.pgstac;

import java.io.IOException;
import java.io.Serializable;
import java.util.Map;
import javax.sql.DataSource;
import org.geoserver.catalog.ResourcePool;
import org.geoserver.config.GeoServer;
import org.geoserver.platform.GeoServerResourceLoader;
import org.geotools.api.data.DataStoreFinder;
import org.geotools.jdbc.JDBCDataStore;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;

/**
 * Lazily builds and refreshes the DBCP2 connection pool backing {@link PgStacServiceInfo}, reusing the PostGIS
 * {@link JDBCDataStore} factory for pool creation only (never for GeoTools data access).
 */
@Component
public class PgStacConnectionManager implements DisposableBean {

    private record PoolState(Map<String, Serializable> params, JDBCDataStore store) {}

    private final GeoServer geoServer;
    private final GeoServerResourceLoader resourceLoader;
    private volatile PoolState state;

    public PgStacConnectionManager(GeoServer geoServer, GeoServerResourceLoader resourceLoader) {
        this.geoServer = geoServer;
        this.resourceLoader = resourceLoader;
    }

    /**
     * Returns the pooled {@link DataSource} for the current {@link PgStacServiceInfo} connection parameters, rebuilding
     * the pool if the configuration changed since the last call. A rebuild closes the previous pool, so a caller must
     * not keep the returned {@link DataSource} across requests.
     */
    public DataSource getDataSource() throws IOException {
        PoolState current = state;
        if (current != null && currentParams().equals(current.params()))
            return current.store().getDataSource();
        // a single rebuild per change, as creating the pool opens a connection to check the database
        synchronized (this) {
            // re-read under the lock, so a caller holding stale config cannot replace a newer pool
            Map<String, Serializable> params = currentParams();
            current = state;
            if (current != null && params.equals(current.params()))
                return current.store().getDataSource();
            JDBCDataStore store = (JDBCDataStore) DataStoreFinder.getDataStore(params);
            if (store == null) {
                throw new IOException("Could not create pgstac connection pool, check connection parameters");
            }
            state = new PoolState(params, store);
            if (current != null) current.store().dispose(); // borrowed connections are closed when returned
            return store.getDataSource();
        }
    }

    private Map<String, Serializable> currentParams() throws IOException {
        PgStacServiceInfo info = geoServer.getService(PgStacServiceInfo.class);
        return ResourcePool.getParams(info.getConnectionParameters(), resourceLoader);
    }

    /** Closes the current pool on GeoServer shutdown; connections still borrowed are closed when returned. */
    @Override
    public synchronized void destroy() {
        PoolState current = state;
        state = null;
        if (current != null) current.store().dispose();
    }
}

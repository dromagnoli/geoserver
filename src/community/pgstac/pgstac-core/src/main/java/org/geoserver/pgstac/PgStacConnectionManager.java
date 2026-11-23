/* (c) 2026 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.pgstac;

import java.io.IOException;
import java.io.Serializable;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
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
    private final AtomicReference<PoolState> state = new AtomicReference<>();

    public PgStacConnectionManager(GeoServer geoServer, GeoServerResourceLoader resourceLoader) {
        this.geoServer = geoServer;
        this.resourceLoader = resourceLoader;
    }

    /**
     * Returns the pooled {@link DataSource} for the current {@link PgStacServiceInfo} connection parameters, rebuilding
     * the pool if the configuration changed since the last call.
     */
    public DataSource getDataSource() throws IOException {
        PgStacServiceInfo info = geoServer.getService(PgStacServiceInfo.class);
        Map<String, Serializable> params = ResourcePool.getParams(info.getConnectionParameters(), resourceLoader);
        // pool creation is unlocked, so concurrent callers can race to rebuild: each builds its own
        // candidate (fresh) and CASes it in; the loser's pool is disposed here rather than left
        // dangling, and it re-reads state to pick up the winner's pool before re-checking params
        PoolState current = state.get();
        while (current == null || !params.equals(current.params())) {
            JDBCDataStore store = (JDBCDataStore) DataStoreFinder.getDataStore(params);
            if (store == null) {
                throw new IOException("Could not create pgstac connection pool, check connection parameters");
            }
            PoolState fresh = new PoolState(params, store);
            if (state.compareAndSet(current, fresh)) {
                if (current != null) current.store().dispose(); // closes idle conns; borrowed ones finish in flight
                current = fresh;
            } else {
                store.dispose(); // lost the race, this pool was never handed out
                current = state.get();
            }
        }
        return current.store().getDataSource();
    }

    /** Closes the current pool on GeoServer shutdown; connections still borrowed are closed when returned. */
    @Override
    public void destroy() {
        PoolState current = state.getAndSet(null);
        if (current != null) current.store().dispose();
    }
}

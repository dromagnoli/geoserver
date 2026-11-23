/* (c) 2026 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.pgstac;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.geoserver.config.ServiceInfo;

/** Configuration of the PgSTAC STAC service: PostGIS connection, service behaviour and security rules. */
public interface PgStacServiceInfo extends ServiceInfo {

    /** Service level gating STAC transaction endpoints; {@link ServiceLevel#DISABLED} by default. */
    enum ServiceLevel {
        DISABLED,
        TRANSACTIONAL
    }

    /** Same parameter set as {@code DataStoreInfo.getConnectionParameters()} (host, db, user, password, ...). */
    Map<String, Serializable> getConnectionParameters();

    void setConnectionParameters(Map<String, Serializable> connectionParameters);

    /** Hard cap on the {@code limit} sent to every PgSTAC search/collection_search call. */
    int getMaxFeatures();

    void setMaxFeatures(int maxFeatures);

    int getDefaultPageSize();

    void setDefaultPageSize(int defaultPageSize);

    /** Maps to pgstac {@code conf.context}: {@code true} sends {@code "off"}, {@code false} sends {@code "counted"}. */
    boolean isSkipNumberMatched();

    void setSkipNumberMatched(boolean skipNumberMatched);

    /** When {@code true}, items are hydrated in GeoServer instead of in the database (see facade hydration). */
    boolean isUseApiHydrate();

    void setUseApiHydrate(boolean useApiHydrate);

    /** TTL of the {@code UniqueVisitor} aggregate cache, in {@link #getAggregatesCacheTTLUnit()} units. */
    int getAggregatesCacheTTL();

    void setAggregatesCacheTTL(int aggregatesCacheTTL);

    TimeUnit getAggregatesCacheTTLUnit();

    void setAggregatesCacheTTLUnit(TimeUnit aggregatesCacheTTLUnit);

    /**
     * Gates STAC transaction endpoints; when {@link ServiceLevel#TRANSACTIONAL}, mutations require an administrator.
     */
    ServiceLevel getServiceLevel();

    void setServiceLevel(ServiceLevel serviceLevel);

    /** Collection visibility rules (see {@link AccessRule}); same semantics as OSEO's collection access limits. */
    List<AccessRule> getCollectionAccessRules();

    void setCollectionAccessRules(List<AccessRule> collectionAccessRules);

    /** Per-collection item visibility rules; same semantics as OSEO's product access limits. */
    List<AccessRule> getItemAccessRules();

    void setItemAccessRules(List<AccessRule> itemAccessRules);
}

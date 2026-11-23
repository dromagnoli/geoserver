/* (c) 2026 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.pgstac;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.geoserver.config.impl.ServiceInfoImpl;

public class PgStacServiceInfoImpl extends ServiceInfoImpl implements PgStacServiceInfo {

    private Map<String, Serializable> connectionParameters = new LinkedHashMap<>();
    private int maxFeatures = 1000;
    private int defaultPageSize = 10;
    private boolean skipNumberMatched;
    private boolean useApiHydrate;
    private int aggregatesCacheTTL = 1;
    private TimeUnit aggregatesCacheTTLUnit = TimeUnit.HOURS;
    private ServiceLevel serviceLevel = ServiceLevel.DISABLED;
    private List<AccessRule> collectionAccessRules = new ArrayList<>();
    private List<AccessRule> itemAccessRules = new ArrayList<>();

    @Override
    public String getType() {
        return "STAC";
    }

    @Override
    public Map<String, Serializable> getConnectionParameters() {
        return connectionParameters;
    }

    @Override
    public void setConnectionParameters(Map<String, Serializable> connectionParameters) {
        this.connectionParameters = connectionParameters;
    }

    @Override
    public int getMaxFeatures() {
        return maxFeatures;
    }

    @Override
    public void setMaxFeatures(int maxFeatures) {
        this.maxFeatures = maxFeatures;
    }

    @Override
    public int getDefaultPageSize() {
        return defaultPageSize;
    }

    @Override
    public void setDefaultPageSize(int defaultPageSize) {
        this.defaultPageSize = defaultPageSize;
    }

    @Override
    public boolean isSkipNumberMatched() {
        return skipNumberMatched;
    }

    @Override
    public void setSkipNumberMatched(boolean skipNumberMatched) {
        this.skipNumberMatched = skipNumberMatched;
    }

    @Override
    public boolean isUseApiHydrate() {
        return useApiHydrate;
    }

    @Override
    public void setUseApiHydrate(boolean useApiHydrate) {
        this.useApiHydrate = useApiHydrate;
    }

    @Override
    public int getAggregatesCacheTTL() {
        return aggregatesCacheTTL;
    }

    @Override
    public void setAggregatesCacheTTL(int aggregatesCacheTTL) {
        this.aggregatesCacheTTL = aggregatesCacheTTL;
    }

    @Override
    public TimeUnit getAggregatesCacheTTLUnit() {
        return aggregatesCacheTTLUnit;
    }

    @Override
    public void setAggregatesCacheTTLUnit(TimeUnit aggregatesCacheTTLUnit) {
        this.aggregatesCacheTTLUnit = aggregatesCacheTTLUnit;
    }

    @Override
    public ServiceLevel getServiceLevel() {
        return serviceLevel;
    }

    @Override
    public void setServiceLevel(ServiceLevel serviceLevel) {
        this.serviceLevel = serviceLevel;
    }

    @Override
    public List<AccessRule> getCollectionAccessRules() {
        return collectionAccessRules;
    }

    @Override
    public void setCollectionAccessRules(List<AccessRule> collectionAccessRules) {
        this.collectionAccessRules = collectionAccessRules;
    }

    @Override
    public List<AccessRule> getItemAccessRules() {
        return itemAccessRules;
    }

    @Override
    public void setItemAccessRules(List<AccessRule> itemAccessRules) {
        this.itemAccessRules = itemAccessRules;
    }
}

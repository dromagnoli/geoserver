package org.geoserver.catalog;

import java.util.List;

public class VirtualCoverage {
    
    String name;

    public VirtualCoverage(String name, CoverageStoreInfo storeInfo, List<String> coverageNames) {
        super();
        this.name = name;
        this.storeInfo = storeInfo;
        this.coverageNames = coverageNames;
    }

    CoverageStoreInfo storeInfo;

    List<String> coverageNames;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public CoverageStoreInfo getStoreInfo() {
        return storeInfo;
    }

    public void setStoreInfo(CoverageStoreInfo storeInfo) {
        this.storeInfo = storeInfo;
    }

    public List<String> getCoverageNames() {
        return coverageNames;
    }

    public void setCoverageNames(List<String> coverageNames) {
        this.coverageNames = coverageNames;
    }
}

package org.geoserver.catalog;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import org.geotools.coverage.grid.io.GridCoverage2DReader;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

/**
 * Class containing main definition of a Virtual Coverage, such as, originating coverageStore and composing coverageNames.
 * 
 * @author Daniele Romagnoli, GeoSolutions SAS
 *
 */
public class VirtualCoverage implements Serializable {
    
    public static String VIRTUAL_COVERAGE = "VIRTUAL_COVERAGE";
    
    public VirtualCoverage(String name, CoverageStoreInfo storeInfo, List<VirtualCoverageBand> coverageBands) {
        super();
        this.name = name;
        this.storeInfo = storeInfo;
        this.coverageBands = coverageBands;
        this.referenceName = getCoverageBands().get(0).getCoverageName();
    }

    CoverageStoreInfo storeInfo;

    List<VirtualCoverageBand> coverageBands;

    String name;
    
    String referenceName;
    
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<VirtualCoverageBand> getCoverageBands() {
        return coverageBands;
    }

    public void setCoverageBands(List<VirtualCoverageBand> coverageBands) {
        this.coverageBands = coverageBands;
    }

    public String getReferenceName() {
        return referenceName;
    }

    public void setReferenceName(String referenceName) {
        this.referenceName = referenceName;
    }

    public CoverageStoreInfo getStoreInfo() {
        return storeInfo;
    }

    public void setStoreInfo(CoverageStoreInfo storeInfo) {
        this.storeInfo = storeInfo;
    }

    public String checkConsistency (List<CoverageInfo> coverages) {
        final int size = coverages.size(); 
        if (size > 1) {
            String consistency = null;
            CoverageInfo referenceCoverage = coverages.get(0);
            for (int i = 1; i < size; i++) {
                CoverageInfo testCoverage = coverages.get(i);
                consistency = checkConsistency (testCoverage, referenceCoverage);
                if (consistency != null) {
                    return consistency;
                }
            }
        }
        return null;
    }

    private static String checkConsistency(CoverageInfo testCoverage, CoverageInfo referenceCoverage) {
        // TODO: Implemement consistency checks
        
        // Check CRS
        CoordinateReferenceSystem testCRS = testCoverage.getCRS();
        CoordinateReferenceSystem refCRS = referenceCoverage.getCRS();

        // Check resolutions and bbox
        
        // Check Dimensions
        
        // Check datatype
        return null;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((coverageBands == null) ? 0 : coverageBands.hashCode());
        result = prime * result + ((name == null) ? 0 : name.hashCode());
//        result = prime * result + ((storeInfo == null) ? 0 : storeInfo.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        VirtualCoverage other = (VirtualCoverage) obj;
        if (coverageBands == null) {
            if (other.coverageBands != null)
                return false;
        } else if (!coverageBands.equals(other.coverageBands))
            return false;
        if (name == null) {
            if (other.name != null)
                return false;
        } else if (!name.equals(other.name))
            return false;
        if (storeInfo == null) {
            if (other.storeInfo != null)
                return false;
        } else if (!storeInfo.equals(other.storeInfo))
            return false;
        return true;
    }

    public CoverageInfo createVirtualCoverageInfo(CoverageStoreInfo coverageStoreInfo,
            GridCoverage2DReader reader, CatalogBuilder builder) throws Exception {
            // 
        builder.setStore(coverageStoreInfo);
        List<CoverageInfo> coverages = new ArrayList<CoverageInfo>(coverageBands.size());
        for (VirtualCoverageBand coverageBand : coverageBands) {
            CoverageInfo info = builder.buildCoverage(reader, coverageBand.getCoverageName(), null);
            coverages.add(info);
        }
        

        
        // CHECK CONSISTENCY
//        String consistencyCheckResult = virtualCoverage.checkConsistency(coverages);
//        if (consistencyCheckResult != null) {
//            error(new ParamResourceModel("creationFailure", this, "composing coverages doesn't respect consistency checks: " + consistencyCheckResult));
//        }
    //    
//        builder.setStore(dsInfo);
//        FeatureTypeInfo fti = builder.buildFeatureType(ds.getFeatureSource(vt.getName()));
//        fti.getMetadata().put(FeatureTypeInfo.JDBC_VIRTUAL_TABLE, vt);
        populateDimensions(coverages);
        
        
        CoverageInfo coverageInfo = coverages.get(0);
        coverageInfo.setName(getName());
        coverageInfo.setNativeCoverageName(getName());
        
        coverageInfo.getMetadata().put(VirtualCoverage.VIRTUAL_COVERAGE, this);
        return coverageInfo;
        }

    private void populateDimensions(List<CoverageInfo> coverages) {
        List<CoverageDimensionInfo> dimensions = new ArrayList<CoverageDimensionInfo>(coverages.size());
        for (CoverageInfo coverageInfo : coverages) {
//            TODO: make sure to use bandselect rule
            CoverageDimensionInfo dimension = coverageInfo.getDimensions().get(0);
            dimension.setName(coverageInfo.getName());
            dimensions.add(dimension);
//            dimensions.add()
        }
        List<CoverageDimensionInfo> originalDimensions = coverages.get(0).getDimensions();
        originalDimensions.clear();
        originalDimensions.addAll(dimensions);
        
    }
}

package org.geoserver.catalog;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import org.geoserver.catalog.VirtualCoverageBands.VirtualCoverageBand;
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
    
    public VirtualCoverage(String name,/* CoverageStoreInfo storeInfo,*/ VirtualCoverageBands coverageBands) {
        super();
        this.name = name;
        /*this.storeInfo = storeInfo;*/
        this.coverageBands = coverageBands;
        this.referenceName = coverageBands.getBand(0).getCoverageName();
    }

//    CoverageStoreInfo storeInfo;

    VirtualCoverageBands coverageBands;

    String name;
    
    String referenceName;
    
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public VirtualCoverageBands getCoverageBands() {
        return coverageBands;
    }

//    public void setCoverageBands(List<VirtualCoverageBand> coverageBands) {
//        this.coverageBands = coverageBands;
//    }

    public String getReferenceName() {
        return referenceName;
    }

    public void setReferenceName(String referenceName) {
        this.referenceName = referenceName;
    }

//    public CoverageStoreInfo getStoreInfo() {
//        return storeInfo;
//    }
//
//    public void setStoreInfo(CoverageStoreInfo storeInfo) {
//        this.storeInfo = storeInfo;
//    }

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
//        result = prime * result + ((name == null) ? 0 : name.hashCode());
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
//        if (name == null) {
//            if (other.name != null)
//                return false;
//        } else if (!name.equals(other.name))
//            return false;
//        if (storeInfo == null) {
//            if (other.storeInfo != null)
//                return false;
//        } else if (!storeInfo.equals(other.storeInfo))
//            return false;
        return true;
    }

    public CoverageInfo createVirtualCoverageInfo(/*String name, */CoverageStoreInfo storeInfo, CatalogBuilder builder) throws Exception {
            // 
        
        Catalog catalog = storeInfo.getCatalog();
        CoverageInfo cinfo = catalog.getFactory().createCoverage();
        
        cinfo.setStore(storeInfo);
        cinfo.getMetadata().put(VirtualCoverage.VIRTUAL_COVERAGE, this);
        cinfo.setName(name);
        cinfo.setNativeCoverageName(name);
        
        GridCoverage2DReader reader = (GridCoverage2DReader) catalog.getResourcePool().getGridCoverageReader(cinfo, name, null);
        builder.setStore(storeInfo);
        
//        for (VirtualCoverageBand coverageBand : coverageBands) {
//        String name = "virtual";
            CoverageInfo info = builder.buildCoverage(reader, name, null);
            info.getMetadata().put(VirtualCoverage.VIRTUAL_COVERAGE, this);
            info.setName(name);
            info.setNativeCoverageName(name);
//
//            cinfo.setStore(csinfo);
//            cinfo.setEnabled(true);
//            info.setAdvertised(false);
//            info.setEnabled(false);
//            coverages.add(info);
//        }
//        

        // CHECK CONSISTENCY
//        String consistencyCheckResult = virtualCoverage.checkConsistency(coverages);
//        if (consistencyCheckResult != null) {
//            error(new ParamResourceModel("creationFailure", this, "composing coverages doesn't respect consistency checks: " + consistencyCheckResult));
//        }
    
        // SAVE TO CATALOG
//        for (CoverageInfo coverage: coverages) {
//            catalog.add(coverage);
//        }
//        
        // CREATE VIRTUAL COVERAGE
            return info;
//        return createVirtualCoverage(catalog, coverages);
        }

//    private CoverageInfo createVirtualCoverage(Catalog catalog, List<CoverageInfo> coverages) {
//
//        CoverageInfo coverageInfo = catalog.getFactory().createCoverage();
//        CoverageInfo refCoverageInfo = coverages.get(0);
//        coverageInfo.setStore(storeInfo);
//        coverageInfo.setEnabled(true);
//
//        coverageInfo.setNamespace(refCoverageInfo.getNamespace());
//        coverageInfo.setNativeCRS(refCoverageInfo.getNativeCRS());
//        coverageInfo.setName(name);
//        coverageInfo.setNativeCoverageName(name);
//        coverageInfo.setSRS(refCoverageInfo.getSRS());
//        coverageInfo.setProjectionPolicy(refCoverageInfo.getProjectionPolicy());
//        cinfo.setNativeBoundingBox(refCoverageInfo.getNativeBoundingBox());
//        cinfo.setLatLonBoundingBox(new ReferencedEnvelope(CoverageStoreUtils.getWGS84LonLatEnvelope(envelope)));
//        cinfo.setGrid(new GridGeometry2D(originalRange, reader.getOriginalGridToWorld(PixelInCell.CELL_CENTER), nativeCRS));
//        cinfo.setTitle(name);
//        cinfo.setDescription(new StringBuilder("Generated from ").append(name).toString());
//
//        // keywords
//        cinfo.getKeywords().add(new Keyword("WCS"));
//        cinfo.getKeywords().add(new Keyword(format.getName()));
//        cinfo.getKeywords().add(new Keyword(name));
//
//        // native format name
//        cinfo.setNativeFormat(format.getName());
//        cinfo.getMetadata().put("dirName", new StringBuilder(store.getName()).append("_").append(name).toString());
//
//            cinfo.getRequestSRS().add(((Identifier) gc.getCoordinateReferenceSystem2D().getIdentifiers().toArray()[0]).toString());
//            cinfo.getResponseSRS().add(((Identifier) gc.getCoordinateReferenceSystem2D().getIdentifiers().toArray()[0]).toString());
//
//                cinfo.getSupportedFormats().add("GIF");
//                cinfo.getSupportedFormats().add("PNG");
//                cinfo.getSupportedFormats().add("JPEG");
//                cinfo.getSupportedFormats().add("TIFF");
//
//        // interpolation methods
//        cinfo.setDefaultInterpolationMethod("nearest neighbor");
//        cinfo.getInterpolationMethods().add("nearest neighbor");
//        cinfo.getInterpolationMethods().add("bilinear");
//        cinfo.getInterpolationMethods().add("bicubic");
//
//        // read parameters (get the params again since we altered the map to optimize the 
//        // coverage read)
//        cinfo.getParameters().putAll(CoverageUtils.getParametersKVP(readParams));
//
//        
//        
//        
//        
//        List<CoverageDimensionInfo> dimensions = mergeDimensions(coverages);
//        List<CoverageDimensionInfo> originalDimensions = coverages.get(0).getDimensions();
//        originalDimensions.clear();
//        originalDimensions.addAll(dimensions);
//        
//        
//        coverageInfo.getMetadata().put(VirtualCoverage.VIRTUAL_COVERAGE, this);
//        return null;
//    }

    private List<CoverageDimensionInfo> mergeDimensions(List<CoverageInfo> coverages) {
        List<CoverageDimensionInfo> dimensions = new ArrayList<CoverageDimensionInfo>(coverages.size());
        for (CoverageInfo coverageInfo : coverages) {
//            TODO: make sure to use bandselect rule
            CoverageDimensionInfo dimension = coverageInfo.getDimensions().get(0);
            dimension.setName(coverageInfo.getName());
            dimensions.add(dimension);
        }
        return dimensions;
        
        
    }
}

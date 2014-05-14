package org.geoserver.catalog;

import java.io.Serializable;
import java.util.List;

import org.geotools.coverage.grid.io.GridCoverage2DReader;

/**
 * Class containing main definition of a Virtual Coverage, such as, originating coverageStore and composing coverageNames.
 * 
 * @author Daniele Romagnoli, GeoSolutions SAS
 * 
 */
public class VirtualCoverage implements Serializable {

    public static String VIRTUAL_COVERAGE = "VIRTUAL_COVERAGE";

    public VirtualCoverage(String name, List<VirtualCoverageBand> coverageBands) {
        super();
        this.name = name;
        this.coverageBands = coverageBands;
        this.referenceName = coverageBands.get(0).getCoverageName();
    }

    List<VirtualCoverageBand> coverageBands;

    private String name;

    /** Sample coverageName for info: It may be removed once we relax constraints */
    private String referenceName;

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

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((coverageBands == null) ? 0 : coverageBands.hashCode());
        // result = prime * result + ((name == null) ? 0 : name.hashCode());
        // result = prime * result + ((storeInfo == null) ? 0 : storeInfo.hashCode());
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
        return true;
    }

    public CoverageInfo createVirtualCoverageInfo(String name, CoverageStoreInfo storeInfo,
            CatalogBuilder builder) throws Exception {
        Catalog catalog = storeInfo.getCatalog();
        CoverageInfo cinfo = catalog.getFactory().createCoverage();

        cinfo.setStore(storeInfo);
        cinfo.getMetadata().put(VirtualCoverage.VIRTUAL_COVERAGE, this);
        cinfo.setName(name);
        cinfo.setNativeCoverageName(name);

        // Get a reader from the pool for this Sample CoverageInfo (we have to pass it down a VirtualCoverage definition
        GridCoverage2DReader reader = (GridCoverage2DReader) catalog.getResourcePool().getGridCoverageReader(cinfo, name, null);
        builder.setStore(storeInfo);

        CoverageInfo info = builder.buildCoverage(reader, name, null);
        info.getMetadata().put(VirtualCoverage.VIRTUAL_COVERAGE, this);
        info.setName(name);
        info.setNativeCoverageName(name);

        // TODO: CHECK CONSISTENCY
        return info;
    }

    public VirtualCoverageBand getBand(int i) {
        return coverageBands.get(i);
    }

    public VirtualCoverageBand getBand(String coverageName) {
        for (VirtualCoverageBand coverageBand : coverageBands) {
            if (coverageBand.getCoverageName().equalsIgnoreCase(coverageName)) {
                return coverageBand;
            }
        }
        return null;
    }

    public int getSize() {
        return coverageBands != null ? coverageBands.size() : 0;
    }
}

package org.geoserver.catalog;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.geotools.coverage.grid.GridCoverage2D;
import org.geotools.coverage.grid.io.GridCoverage2DReader;
import org.geotools.coverage.grid.io.OverviewPolicy;
import org.geotools.geometry.GeneralEnvelope;
import org.opengis.coverage.grid.GridEnvelope;
import org.opengis.parameter.GeneralParameterValue;
import org.opengis.parameter.ParameterDescriptor;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.datum.PixelInCell;
import org.opengis.referencing.operation.MathTransform;

public class VirtualCoverageReader extends SingleGridCoverage2DReader {
    
    private VirtualCoverage virtualCoverage;
    
    private String referenceName;
    
    private GridCoverage2DReader delegate;
    
//    private Catalog catalog;
    public VirtualCoverageReader(GridCoverage2DReader delegate, String coverageName, VirtualCoverage virtualCoverage, Catalog catalog) {
        super(delegate, coverageName);
        this.delegate = delegate;
        this.virtualCoverage = virtualCoverage;
//        this.catalog = catalog;
        referenceName = virtualCoverage.getReferenceName();
    }
    @Override
    public String[] getMetadataNames() throws IOException {
        return super.getMetadataNames(referenceName);
    }
    @Override
    public String getMetadataValue(String name) throws IOException {
        return super.getMetadataValue(referenceName, name);
    }
    @Override
    public GeneralEnvelope getOriginalEnvelope() {
        return super.getOriginalEnvelope(referenceName);
    }
    @Override
    public GridEnvelope getOriginalGridRange() {
        return super.getOriginalGridRange(referenceName);
    }
    @Override
    public MathTransform getOriginalGridToWorld(PixelInCell pixInCell) {
        return super.getOriginalGridToWorld(referenceName, pixInCell);
    }
    @Override
    public GridCoverage2D read(GeneralParameterValue[] parameters) throws IllegalArgumentException,
            IOException {
        // TODO: ask Nicola for BandMerges
        List<VirtualCoverageBand> bands = virtualCoverage.getCoverageBands();
        List<GridCoverage2D> coverages = new ArrayList<GridCoverage2D>();
        
        // Use composition rule specific implementation
        for (VirtualCoverageBand band: bands) {
            String coverageName = band.getCoverageName();
            SingleGridCoverage2DReader reader = SingleGridCoverage2DReader.wrap(delegate, coverageName);
            coverages.add(reader.read(parameters));
        }
        
        
        return super.read(referenceName, parameters);
    }
    @Override
    public CoordinateReferenceSystem getCoordinateReferenceSystem() {
        return super.getCoordinateReferenceSystem(referenceName);
    }
    @Override
    public Set<ParameterDescriptor<List>> getDynamicParameters() throws IOException {
        return super.getDynamicParameters(referenceName);
    }
    @Override
    public double[] getReadingResolutions(OverviewPolicy policy, double[] requestedResolution)
            throws IOException {
        return super.getReadingResolutions(referenceName, policy, requestedResolution);
    }
    @Override
    public int getNumOverviews() {
        return super.getNumOverviews(referenceName);
    }
    @Override
    public double[][] getResolutionLevels() throws IOException {
        return super.getResolutionLevels(referenceName);
    }
    
    /**
     * Checks the specified name is the one we are expecting
     * @param coverageName
     */
    protected void checkCoverageName(String coverageName) {
//        if (!this.coverageName.equals(coverageName)) {
//            throw new IllegalArgumentException("Unkonwn coverage named " + coverageName
//                    + ", the only valid value is: " + this.coverageName);
//        }
    }
}

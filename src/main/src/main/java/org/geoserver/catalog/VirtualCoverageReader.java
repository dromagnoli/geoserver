package org.geoserver.catalog;

import java.awt.image.RenderedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import javax.media.jai.operator.BandMergeDescriptor;

import org.geoserver.catalog.CoverageDimensionCustomizerReader.CoverageDimensionCustomizerStructuredReader;
import org.geoserver.catalog.VirtualCoverageBands.VirtualCoverageBand;
import org.geotools.coverage.CoverageFactoryFinder;
import org.geotools.coverage.GridSampleDimension;
import org.geotools.coverage.grid.GridCoverage2D;
import org.geotools.coverage.grid.GridCoverageFactory;
import org.geotools.coverage.grid.io.GridCoverage2DReader;
import org.geotools.coverage.grid.io.OverviewPolicy;
import org.geotools.coverage.grid.io.StructuredGridCoverage2DReader;
import org.geotools.geometry.GeneralEnvelope;
import org.opengis.coverage.SampleDimension;
import org.opengis.coverage.grid.GridCoverageReader;
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
    
    private CoverageInfo coverageInfo;
    
    private GridCoverageFactory coverageFactory;
    
//      private Catalog catalog;
    public VirtualCoverageReader(GridCoverage2DReader delegate, VirtualCoverage virtualCoverage, CoverageInfo coverageInfo) {
        super(delegate, /*virtualCoverage.getName()*/ coverageInfo.getName());
        this.delegate = delegate;
        this.virtualCoverage = virtualCoverage;
        this.coverageInfo = coverageInfo;
//        this.catalog = catalog;
        referenceName = virtualCoverage.getReferenceName();

        //TODO: rendering Hints 
        coverageFactory = CoverageFactoryFinder.getGridCoverageFactory(null);
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
        VirtualCoverageBands bands = virtualCoverage.getCoverageBands();
        List<GridCoverage2D> coverages = new ArrayList<GridCoverage2D>();
        List<SampleDimension> dims = new ArrayList<SampleDimension>();
        
        // Use composition rule specific implementation
        final int bandSize = bands.getSize();
        for (int i=0; i < bandSize; i++) {
            VirtualCoverageBand band = bands.getBand(i);
            String coverageName = band.getCoverageName();
            GridCoverageReader reader = wrap(delegate, coverageName, coverageInfo);
            GridCoverage2D coverage = (GridCoverage2D)reader.read(parameters);
            coverages.add(coverage);
            dims.addAll(Arrays.asList(coverage.getSampleDimensions()));
        }
        
        
        GridCoverage2D sampleCoverage = coverages.get(0);
        RenderedImage image = BandMergeDescriptor.create(sampleCoverage.getRenderedImage(), coverages.get(1).getRenderedImage(), null);
        GridSampleDimension[] wrappedDims = new GridSampleDimension[bandSize];
        
        if (coverageInfo.getDimensions() != null) {
            int i = 0;
//            for (SampleDimension dim: dims) {
//                wrappedDims[i] = new WrappedSampleDimension((GridSampleDimension) dim, 
//                        storedDimensions.get(outputDims != inputDims ? (i > (inputDims - 1 ) ? inputDims - 1 : i) : i));
//                i++;
//            }
        }
        return coverageFactory.create(coverageInfo.getName()/*virtualCoverage.getName()*/, image, sampleCoverage.getGridGeometry(), dims.toArray(new GridSampleDimension[dims.size()]), null, /*props*/ null);
    }
    
    public static GridCoverageReader wrap(GridCoverage2DReader delegate, String coverageName, CoverageInfo info) {
        GridCoverage2DReader reader = delegate;
        if (coverageName != null) {
            reader = SingleGridCoverage2DReader.wrap(delegate, coverageName);
        }
        if (reader instanceof StructuredGridCoverage2DReader) {
            return new CoverageDimensionCustomizerStructuredReader((StructuredGridCoverage2DReader) reader, coverageName, info);
        } else {
            return new CoverageDimensionCustomizerReader(reader, coverageName, info);
        }
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

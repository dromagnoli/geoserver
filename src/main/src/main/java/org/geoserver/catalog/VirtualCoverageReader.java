package org.geoserver.catalog;

import java.awt.image.RenderedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import javax.media.jai.operator.BandMergeDescriptor;

import org.geoserver.catalog.CoverageDimensionCustomizerReader.CoverageDimensionCustomizerStructuredReader;
import org.geotools.coverage.CoverageFactoryFinder;
import org.geotools.coverage.GridSampleDimension;
import org.geotools.coverage.grid.GridCoverage2D;
import org.geotools.coverage.grid.GridCoverageFactory;
import org.geotools.coverage.grid.io.GridCoverage2DReader;
import org.geotools.coverage.grid.io.OverviewPolicy;
import org.geotools.coverage.grid.io.StructuredGridCoverage2DReader;
import org.geotools.factory.Hints;
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
    
    private Hints hints;
    
    private CoverageInfo coverageInfo;
    
    private GridCoverageFactory coverageFactory;
    
    public VirtualCoverageReader(GridCoverage2DReader delegate, VirtualCoverage virtualCoverage, CoverageInfo coverageInfo, Hints hints) {
        super(delegate, virtualCoverage.getName());
        this.delegate = delegate;
        this.virtualCoverage = virtualCoverage;
        this.coverageInfo = coverageInfo;
        this.hints = hints;
        referenceName = virtualCoverage.getReferenceName();
        if (this.hints != null && this.hints.containsKey(Hints.GRID_COVERAGE_FACTORY)) {
            final Object factory = this.hints.get(Hints.GRID_COVERAGE_FACTORY);
            if (factory != null && factory instanceof GridCoverageFactory) {
                this.coverageFactory = (GridCoverageFactory) factory;
            }
        }
        if (this.coverageFactory == null) {
            this.coverageFactory = CoverageFactoryFinder.getGridCoverageFactory(this.hints);
        }
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

        List<VirtualCoverageBand> bands = virtualCoverage.getCoverageBands();
        List<GridCoverage2D> coverages = new ArrayList<GridCoverage2D>();
        List<SampleDimension> dims = new ArrayList<SampleDimension>();
        
        // Use composition rule specific implementation
        final int bandSize = bands.size();
//        for (int i=0; i < bandSize; i++) {
        for (VirtualCoverageBand band : bands) {
//            VirtualCoverageBand band = bands.getBand(i);
            String coverageName = band.getCoverageName();
            GridCoverageReader reader = wrap(delegate, coverageName, coverageInfo);
            GridCoverage2D coverage = (GridCoverage2D)reader.read(parameters);
            coverages.add(coverage);
            dims.addAll(Arrays.asList(coverage.getSampleDimensions()));
        }
        
        
        GridCoverage2D sampleCoverage = coverages.get(0);

        // TODO: Implement bandMerges
        RenderedImage image = BandMergeDescriptor.create(sampleCoverage.getRenderedImage(), coverages.get(1).getRenderedImage(), null);
//        image.getData(new Rectangle(0,0,1000,1000));
//        ImageIO.write(coverages.get(1).getRenderedImage(), "tiff", new File("C:\\merged2.tif"));
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
     * @param coverageName
     */
    protected void checkCoverageName(String coverageName) {
        // It's virtual... TODO: add checks 
        
    }
}

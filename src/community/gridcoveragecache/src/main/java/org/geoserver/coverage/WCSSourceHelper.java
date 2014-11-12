/* (c) 2014 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.coverage;

import java.awt.Rectangle;
import java.awt.geom.Rectangle2D;
import java.awt.image.RenderedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.media.jai.ImageLayout;
import javax.media.jai.Interpolation;
import javax.media.jai.InterpolationBicubic2;
import javax.media.jai.InterpolationBilinear;
import javax.media.jai.InterpolationNearest;
import javax.media.jai.operator.ConstantDescriptor;

import net.opengis.wcs20.DimensionSliceType;
import net.opengis.wcs20.DimensionSubsetType;
import net.opengis.wcs20.DimensionTrimType;
import net.opengis.wcs20.ExtensionItemType;
import net.opengis.wcs20.ExtensionType;
import net.opengis.wcs20.GetCoverageType;
import net.opengis.wcs20.InterpolationMethodType;
import net.opengis.wcs20.InterpolationType;
import net.opengis.wcs20.ScaleToSizeType;
import net.opengis.wcs20.ScalingType;
import net.opengis.wcs20.TargetAxisSizeType;
import net.opengis.wcs20.Wcs20Factory;

import org.eclipse.emf.common.util.EList;
import org.geoserver.coverage.layer.CoverageMetaTile;
import org.geoserver.coverage.layer.CoverageTileLayer;
import org.geoserver.platform.GeoServerExtensions;
import org.geoserver.wcs2_0.DefaultWebCoverageService20;
import org.geoserver.wcs2_0.WCS20Const;
import org.geotools.coverage.grid.GeneralGridEnvelope;
import org.geotools.coverage.grid.GridCoverage2D;
import org.geotools.coverage.grid.GridCoverageFactory;
import org.geotools.coverage.grid.GridGeometry2D;
import org.geotools.coverage.grid.io.OverviewPolicy;
import org.geotools.coverage.processing.CoverageProcessor;
import org.geotools.coverage.processing.operation.Mosaic;
import org.geotools.factory.GeoTools;
import org.geotools.geometry.GeneralEnvelope;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.referencing.CRS;
import org.geotools.resources.image.ImageUtilities;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.conveyor.ConveyorTile;
import org.geowebcache.grid.BoundingBox;
import org.geowebcache.grid.GridSubset;
import org.opengis.parameter.ParameterValueGroup;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.NoSuchAuthorityCodeException;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

public class WCSSourceHelper {

    private final static Logger LOGGER = org.geotools.util.logging.Logging
            .getLogger(WCSSourceHelper.class);

    
    private static final String INTERPOLATION_CUBIC = "http://www.opengis.net/def/interpolation/OGC/1/cubic";

    private static final String INTERPOLATION_NEAREST = "http://www.opengis.net/def/interpolation/OGC/1/nearest-neighbor";

    private static final String INTERPOLATION_BILINEAR = "http://www.opengis.net/def/interpolation/OGC/1/linear";

    private static final String AXIS_Y = "http://www.opengis.net/def/axis/OGC/1/j";

    private static final String AXIS_X = "http://www.opengis.net/def/axis/OGC/1/i";

    private static final String DOUBLE_UNDERSCORE = "__";

    private static final String WCS_SERVICE_NAME = "WCS";

    private static final String WCS_VERSION = "2.0.1";

    public static final String TIME = "TIME";

    public static final String ELEVATION = "ELEVATION";

    private static final String DIMENSION_LONG = "http://www.opengis.net/def/axis/OGC/0/Long";

    private static final String DIMENSION_LAT = "http://www.opengis.net/def/axis/OGC/0/Lat";

    private static final String DIMENSION_TIME = "http://www.opengis.net/def/axis/OGC/0/time";

    private static final String DIMENSION_ELEVATION = "http://www.opengis.net/def/axis/OGC/0/elevation";

    private static final Wcs20Factory WCS20_FACTORY = Wcs20Factory.eINSTANCE;

    private static final CoverageProcessor processor = CoverageProcessor.getInstance(GeoTools
            .getDefaultHints());

    private static final Mosaic MOSAIC = (Mosaic) processor.getOperation("Mosaic");

    private DefaultWebCoverageService20 service;

    private CoverageTileLayer layer;

    private final static Map<Integer, CoordinateReferenceSystem> crsCache = new HashMap<Integer, CoordinateReferenceSystem>(); 

    static {
        final int EPSG_4326 = 4326;
        try {
            crsCache.put(EPSG_4326, getCRS(EPSG_4326));
        } catch ( FactoryException e) {
            throw new RuntimeException(e);
        } 
    }
    
    public WCSSourceHelper(CoverageTileLayer layer) {
        this.layer = layer;
        List<DefaultWebCoverageService20> extensions = GeoServerExtensions
                .extensions(DefaultWebCoverageService20.class);
        service = extensions.get(0);
    }

    public void makeRequest(CoverageMetaTile metaTile, ConveyorTile tile, Interpolation interpolation) throws GeoWebCacheException {
        final GridSubset gridSubset = layer.getGridSubset(tile.getGridSetId());

        final GetCoverageType request = setupGetCoverageRequest(metaTile, tile, gridSubset, interpolation);

        // Getting Metatile properties
        final BoundingBox bbox = metaTile.getMetaTileBounds();
        final int width = metaTile.getMetaTileWidth();
        final int height = metaTile.getMetaTileHeight();

        // Checking if the MetaTile BoundingBox intersects with the Coverage BBOX
        boolean intersection = true;
        try {
            ReferencedEnvelope layerBBOX = layer.getBbox();
            int code = metaTile.getSRS().getNumber();
            CoordinateReferenceSystem tileCRS = getCRS(code);
            ReferencedEnvelope tileBBOX = new ReferencedEnvelope(bbox.getMinX(), bbox.getMaxX(),
                    bbox.getMinY(), bbox.getMaxY(), tileCRS);
            intersection = layerBBOX.intersects(tileBBOX.toBounds(tileCRS));
        } catch (Exception e) {
            throw new GeoWebCacheException(e);
        }

        final GridCoverage2D coverage;

        if (intersection) {
            coverage = (GridCoverage2D) service.getCoverage(request);
        } else {
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.fine("Metatile bbox doesn't intersect the coverage bbox; Returning constant image");
            }
            ImageLayout layout = layer.getLayout();
            Number[] backgroundValues = ImageUtilities.getBackgroundValues(layout.getSampleModel(null), null);
            RenderedImage constant = ConstantDescriptor.create(width * 1.0f, height * 1.0f, backgroundValues, null);
            coverage = (GridCoverage2D) new GridCoverageFactory().create("empty", constant, new GeneralEnvelope(new Rectangle2D.Double(bbox.getMinX(),
                    bbox.getMinY(), bbox.getWidth(), bbox.getHeight())));
        }

        // WCS May return an area which is smaller then requested since it's internally
        // doing an intersection between the requested envelope and the
        // original coverage envelope. We need to properly fill the GridSet tile
        // using a mosaic operation

        // Creation of a List of the input Sources
        List<GridCoverage2D> sources = new ArrayList<GridCoverage2D>(2);
        sources.add(coverage);

        
        ParameterValueGroup param = MOSAIC.getParameters();
        // Setting of the sources
        param.parameter("Sources").setValue(sources);

        // Setting the imposed GridGeometry to satisfy the request.
        final GridGeometry2D ggStart = new GridGeometry2D(new GeneralGridEnvelope(new Rectangle(0,
                0, width, height)), new GeneralEnvelope(new Rectangle2D.Double(bbox.getMinX(),
                bbox.getMinY(), bbox.getWidth(), bbox.getHeight())));

        param.parameter("geometry").setValue(ggStart);

        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.fine("Adding border to the read coverage through mosaic operation "
                    + "to satisfy the original request, using the specified bbox: " + bbox);
        }
        // Mosaic
        final GridCoverage2D mosaic = (GridCoverage2D) processor.doOperation(param);
        metaTile.setImage(mosaic.getRenderedImage());
    }

    private static CoordinateReferenceSystem getCRS(int code) throws NoSuchAuthorityCodeException, FactoryException {
        // Working against a non synchronized cache shouldn't be an issue
        // In the worst case, we will add the same element more times when 
        // not available
        CoordinateReferenceSystem crs = null;
        if (!crsCache.containsKey(code)) {
            crs = CRS.decode("EPSG:" + code);
            crsCache.put(code, crs);
        } else {
            crs = crsCache.get(code);
        }
        return crs;
    }

    /**
     * Setup a proper WCS 2.0 getCoverage request by inspecting tile bbox and forcing size.
     * 
     * @param metaTile
     * @param tile
     * @param gridSubset
     * @return
     */
    private GetCoverageType setupGetCoverageRequest(CoverageMetaTile metaTile, ConveyorTile tile,
            GridSubset gridSubset, Interpolation interpolation) {

        // // 
        // Setting base getCoverage request parameters
        // //
        final GetCoverageType getCoverage = WCS20_FACTORY.createGetCoverageType();
        getCoverage.setVersion(WCS_VERSION);
        getCoverage.setService(WCS_SERVICE_NAME);
        getCoverage.setCoverageId(layer.getWorkspaceName() + DOUBLE_UNDERSCORE
                + layer.getCoverageName());

        // //
        // Setting dimensions (long/lat, time, elevation, ...)
        // //
        setDimensions(getCoverage, metaTile, tile);

        // //
        // Setting output size
        // //
        setScaling(getCoverage, metaTile);

        // //
        // Setting interpolation
        // //
        setInterpolation(getCoverage, interpolation);
        
        // //
        // Setting overview policy
        // //
        setOverviewPolicy(getCoverage);
        return getCoverage;
    }

    private void setOverviewPolicy(GetCoverageType getCoverage) {
        ExtensionType extension = getCoverage.getExtension();

        final EList<ExtensionItemType> content = extension.getContents();
        final ExtensionItemType extensionItem = WCS20_FACTORY.createExtensionItemType();

        extensionItem.setName(WCS20Const.OVERVIEW_POLICY_EXTENSION);
        // Should we change that policy?
        extensionItem.setSimpleContent(OverviewPolicy.QUALITY.name());
        content.add(extensionItem);
    }

    /**
     * Set the interpolation extension to the WCS 2.0 get coverage request.
     * @param getCoverage
     * @param interpolation
     */
    private void setInterpolation(GetCoverageType getCoverage, Interpolation interpolation) {
        ExtensionType extension = getCoverage.getExtension();

        final EList<ExtensionItemType> content = extension.getContents();
        final ExtensionItemType extensionItem = WCS20_FACTORY.createExtensionItemType();
        final InterpolationType interpolationType = WCS20_FACTORY.createInterpolationType();

        extensionItem.setName("Interpolation");
        extensionItem.setObjectContent(interpolationType);
        content.add(extensionItem);

        final InterpolationMethodType interpolationMethodType = WCS20_FACTORY.createInterpolationMethodType();
        String interpolationMethod = getInterpolationMethod(interpolation);
        interpolationMethodType.setInterpolationMethod(interpolationMethod);
        interpolationType.setInterpolationMethod(interpolationMethodType);
    }

    /** 
     * Retrieve a proper Interpolation method policy from the provided JAI interpolation instance 
     */
    private String getInterpolationMethod(Interpolation interpolation) {
        if (interpolation instanceof InterpolationNearest) {
            return INTERPOLATION_NEAREST;
        } else if (interpolation instanceof InterpolationBilinear) {
            return INTERPOLATION_BILINEAR;
        } else if (interpolation instanceof InterpolationBicubic2) {
            return INTERPOLATION_CUBIC;
        } else {
            throw new IllegalArgumentException("Unsupported interpolation type: " + interpolation);
        }
    }

    /**
     * Set the scaling extension to the WCS 2.0 getCoverage request
     * 
     * @param getCoverage
     * @param metaTile
     */
    private void setScaling(GetCoverageType getCoverage, CoverageMetaTile metaTile) {
        final int width = metaTile.getMetaTileWidth();
        final int height = metaTile.getMetaTileHeight();
        final ExtensionType extension = WCS20_FACTORY.createExtensionType();
        getCoverage.setExtension(extension);

        final EList<ExtensionItemType> content = extension.getContents();
        final ExtensionItemType extensionItem = WCS20_FACTORY.createExtensionItemType();
        final ScalingType scalingType = WCS20_FACTORY.createScalingType();

        extensionItem.setName("Scaling");
        extensionItem.setObjectContent(scalingType);
        content.add(extensionItem);

        final ScaleToSizeType scaleToSize = WCS20_FACTORY.createScaleToSizeType();
        scalingType.setScaleToSize(scaleToSize);

        final TargetAxisSizeType lonScalingValue = WCS20_FACTORY.createTargetAxisSizeType();
        lonScalingValue.setAxis(AXIS_X);
        lonScalingValue.setTargetSize(width);

        final TargetAxisSizeType latScalingValue = WCS20_FACTORY.createTargetAxisSizeType();
        latScalingValue.setAxis(AXIS_Y);
        latScalingValue.setTargetSize(height);

        final EList<TargetAxisSizeType> targets = scaleToSize.getTargetAxisSize();
        targets.add(lonScalingValue);
        targets.add(latScalingValue);
    }

    /**
     * Setting dimensions extension on WCS 2.0 request, needed to do long/lat selection (trimming on the axis),
     * time and elevation subsetting and so on.
     * 
     * @param getCoverage
     * @param metaTile
     * @param tile
     * 
     * @TODO Need to add support for custom dimensions
     */
    private void setDimensions(GetCoverageType getCoverage, CoverageMetaTile metaTile, ConveyorTile tile) {
        final BoundingBox bbox = metaTile.getMetaTileBounds();
        Map<String, String> parameters = tile.getFullParameters();
        final EList<DimensionSubsetType> dimensionSubset = getCoverage.getDimensionSubset();

        final DimensionTrimType trimLon = WCS20_FACTORY.createDimensionTrimType();
        trimLon.setDimension(DIMENSION_LONG);
        trimLon.setTrimLow(Double.toString(bbox.getMinX()));
        trimLon.setTrimHigh(Double.toString(bbox.getMaxX()));
        dimensionSubset.add(trimLon);

        final DimensionTrimType trimLat = WCS20_FACTORY.createDimensionTrimType();
        trimLat.setDimension(DIMENSION_LAT);
        trimLat.setTrimLow(Double.toString(bbox.getMinY()));
        trimLat.setTrimHigh(Double.toString(bbox.getMaxY()));
        dimensionSubset.add(trimLat);

        if (parameters != null && parameters.size() > 0) {
            if (parameters.containsKey(TIME)) {
                final DimensionSliceType sliceTime = WCS20_FACTORY.createDimensionSliceType();
                sliceTime.setDimension(DIMENSION_TIME);
                sliceTime.setSlicePoint(parameters.get(TIME));
                dimensionSubset.add(sliceTime);
            }
            if (parameters.containsKey(ELEVATION)) {
                final DimensionSliceType sliceElevation = WCS20_FACTORY.createDimensionSliceType();
                sliceElevation.setDimension(DIMENSION_ELEVATION);
                sliceElevation.setSlicePoint(parameters.get(ELEVATION));
                dimensionSubset.add(sliceElevation);
            }

            // TODO: Deal with other dimensions
        }
    }
}

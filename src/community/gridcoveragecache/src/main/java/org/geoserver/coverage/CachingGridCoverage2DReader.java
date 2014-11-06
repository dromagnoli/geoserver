/* (c) 2014 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.coverage;

import java.io.IOException;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.media.jai.ImageLayout;

import org.geoserver.catalog.CoverageInfo;
import org.geoserver.catalog.LayerInfo;
import org.geoserver.catalog.ResourcePool;
import org.geoserver.gwc.GWC;
import org.geoserver.gwc.layer.CatalogConfiguration;
import org.geoserver.gwc.layer.GeoServerTileLayer;
import org.geoserver.platform.GeoServerExtensions;
import org.geoserver.util.ISO8601Formatter;
import org.geotools.coverage.grid.GridCoverage2D;
import org.geotools.coverage.grid.GridCoverageFactory;
import org.geotools.coverage.grid.GridEnvelope2D;
import org.geotools.coverage.grid.GridGeometry2D;
import org.geotools.coverage.grid.io.AbstractGridFormat;
import org.geotools.coverage.grid.io.DimensionDescriptor;
import org.geotools.coverage.grid.io.GranuleSource;
import org.geotools.coverage.grid.io.GridCoverage2DReader;
import org.geotools.coverage.grid.io.HarvestedSource;
import org.geotools.coverage.grid.io.OverviewPolicy;
import org.geotools.coverage.grid.io.StructuredGridCoverage2DReader;
import org.geotools.factory.Hints;
import org.geotools.geometry.GeneralEnvelope;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.renderer.lite.RendererUtilities;
import org.geowebcache.conveyor.ConveyorTile;
import org.geowebcache.grid.BoundingBox;
import org.geowebcache.grid.Grid;
import org.geowebcache.grid.GridSet;
import org.geowebcache.grid.GridSetBroker;
import org.geowebcache.grid.GridSubset;
import org.geowebcache.grid.GridSubsetFactory;
import org.geowebcache.grid.OutsideCoverageException;
import org.geowebcache.mime.MimeException;
import org.geowebcache.mime.MimeType;
import org.geowebcache.storage.StorageBroker;
import org.jaitools.imageutils.ImageLayout2;
import org.opengis.coverage.grid.Format;
import org.opengis.coverage.grid.GridEnvelope;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.geometry.Envelope;
import org.opengis.parameter.GeneralParameterDescriptor;
import org.opengis.parameter.GeneralParameterValue;
import org.opengis.parameter.ParameterDescriptor;
import org.opengis.parameter.ParameterValue;
import org.opengis.referencing.ReferenceIdentifier;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.datum.PixelInCell;
import org.opengis.referencing.operation.MathTransform;

public class CachingGridCoverage2DReader implements GridCoverage2DReader {

    private final static Logger LOGGER = org.geotools.util.logging.Logging
            .getLogger(CachingGridCoverage2DReader.class);

    private static final MimeType TIFF_MIME_TYPE;

    private ISO8601Formatter formatter = new ISO8601Formatter();

    static {
        try {
            TIFF_MIME_TYPE = MimeType.createFromExtension("tiff");
        } catch (MimeException e) {
            throw new RuntimeException("Exception occurred while getting TIFF mimetype", e);
        }

    }

    private GridCoverage2DReader delegate;

   // private CoverageInfo info;

    private WCSLayer wcsLayer;

    private GridCoveragesCache cache;

    private List<GridSubset> gridSubSet;// TODO CHANGE HERE

    private boolean axisOrderingTopDown;

    private GridSet gridSet;

    private static final GridCoverageFactory gcf = new GridCoverageFactory();

    public static CachingGridCoverage2DReader wrap(ResourcePool pool, GridCoveragesCache cache,
            CoverageInfo info, String coverageName, Hints hints) throws IOException {
        Hints localHints = null;

        // Set hints to exclude gridCoverage extensions lookup to go through
        // the standard gridCoverage reader lookup on ResourcePool
        Hints newHints = new Hints(ResourcePool.SKIP_COVERAGE_EXTENSIONS_LOOKUP, true);
        if (hints != null) {
            localHints = hints.clone();
            localHints.add(newHints);
        } else {
            localHints = newHints;
        }
//        localHints.add(new Hints(ResourcePool.SKIP_READER_WRAPPING, true));
        GridCoverage2DReader delegate = (GridCoverage2DReader) pool.getGridCoverageReader(info,
                coverageName, localHints);
        if (delegate instanceof StructuredGridCoverage2DReader) {
            return new CachingStructuredGridCoverage2DReader(cache, info,
                    (StructuredGridCoverage2DReader) delegate);
        } else {
            return new CachingGridCoverage2DReader(cache, info, delegate);
        }

    }

    public CachingGridCoverage2DReader(GridCoveragesCache cache,
            CoverageInfo info, GridCoverage2DReader reader) {
        //this.info = info;
        this.cache = cache;
        try {
            delegate = reader;
            gridSubSet = buildGridSubSet();
            String coverageName = info.getNativeName();
            ImageLayout layout = reader.getImageLayout(coverageName);  
            //wcsLayer = new WCSLayer(info, cache.getGridSetBroker(), gridSubSet, layout);
            //ImageLayout layout = reader.getImageLayout();
            LayerInfo layerInfo = GWC.get().getCatalog().getLayers(info).get(0);
            GeoServerTileLayer tileLayerByName = (GeoServerTileLayer) GWC.get().getTileLayerByName(layerInfo.getName());
            wcsLayer = new WCSLayer(info, cache.getGridSetBroker(), gridSubSet, layout, tileLayerByName.getInfo());
            List<CatalogConfiguration> extensions = GeoServerExtensions
                    .extensions(CatalogConfiguration.class);
            //CatalogConfiguration config = extensions.get(0);
//            if (!config.containsLayer(wcsLayer.getId())) {
//                config.addLayer(wcsLayer);
//            }
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

    private List<GridSubset> buildGridSubSet() throws IOException {
        gridSet = buildGridSet();
        axisOrderingTopDown = axisOrderingTopDown();
//        GeneralEnvelope env = getOriginalEnvelope();
//        GridSubsetFactory.createGridSubSet(gridSet)
        return Arrays.asList(GridSubsetFactory.createGridSubSet(gridSet/*, new BoundingBox(
                env.getMinimum(0), env.getMinimum(1), env.getMaximum(0), env.getMaximum(1)), null,
                null*/)); // TODO CHANGE HERE
    }

    GridSet buildGridSet() throws IOException {
        // TODO: Replace that using global GridSet
        GridSetBroker broker = cache.getGridSetBroker();

        // TODO: Support different grids
        // GridSet set = broker.get(broker.WORLD_EPSG4326.getName());
        GridSet set = broker.get(cache.REFERENCE.getName());
        return set;

        // Previous code for dynamic gridSet 
        //
        // int epsgCode = 4326;
        // String name = info.getName() + "_" + epsgCode + "_" + 1;
        // SRS srs = SRS.getSRS(epsgCode);
        // GeneralEnvelope envelope = delegate.getOriginalEnvelope();
        // BoundingBox extent = new BoundingBox(envelope.getMinimum(0), envelope.getMinimum(1),
        // envelope.getMaximum(0), envelope.getMaximum(1));
        // double[][] resolution = delegate.getResolutionLevels();
        // return GridSetFactory.createGridSet(name, srs, extent, true /*CHECKTHAT*/, 3 /*CHECKTHAT_LEVELS*/,
        // 1d /*CHECKTHAT_METERS_PER_UNIT*/,
        // resolution[0][0] /*CHECKTHAT_PIXELSIZE*/,
        // 512/*CHECKTHAT_TILEWIDTH*/ , 512/*CHECKTHAT_TILEHEIGHT*/ ,
        // false /*CHECKTHAT_yCoordinateFirst*/);
        //
    }

    @Override
    public Format getFormat() {
        return delegate.getFormat();
    }

    @Override
    public Object getSource() {
        return delegate.getSource();
    }

    @Override
    public String[] getMetadataNames() throws IOException {
        return delegate.getMetadataNames();
    }

    @Override
    public String[] getMetadataNames(String coverageName) throws IOException {
        return delegate.getMetadataNames(coverageName);
    }

    @Override
    public String getMetadataValue(String name) throws IOException {
        return delegate.getMetadataValue(name);
    }

    @Override
    public String getMetadataValue(String coverageName, String name) throws IOException {
        return delegate.getMetadataValue(coverageName, name);
    }

    @Override
    public String[] listSubNames() throws IOException {
        return delegate.listSubNames();
    }

    @Override
    public String[] getGridCoverageNames() throws IOException {
        return delegate.getGridCoverageNames();
    }

    @Override
    public int getGridCoverageCount() throws IOException {
        return delegate.getGridCoverageCount();
    }

    @Override
    public String getCurrentSubname() throws IOException {
        return delegate.getCurrentSubname();
    }

    @Override
    public boolean hasMoreGridCoverages() throws IOException {
        return delegate.hasMoreGridCoverages();
    }

    @Override
    public void skip() throws IOException {
        delegate.skip();
    }

    @Override
    public void dispose() throws IOException {
        delegate.dispose();

    }

    @Override
    public GeneralEnvelope getOriginalEnvelope() {
        return delegate.getOriginalEnvelope();
    }

    @Override
    public GeneralEnvelope getOriginalEnvelope(String coverageName) {
        return delegate.getOriginalEnvelope(coverageName);
    }

    @Override
    public CoordinateReferenceSystem getCoordinateReferenceSystem() {
        return delegate.getCoordinateReferenceSystem();
    }

    @Override
    public CoordinateReferenceSystem getCoordinateReferenceSystem(String coverageName) {
        return delegate.getCoordinateReferenceSystem(coverageName);
    }

    @Override
    public GridEnvelope getOriginalGridRange() {
        return delegate.getOriginalGridRange();
    }

    @Override
    public GridEnvelope getOriginalGridRange(String coverageName) {
        return delegate.getOriginalGridRange(coverageName);
    }

    @Override
    public MathTransform getOriginalGridToWorld(PixelInCell pixInCell) {
        return delegate.getOriginalGridToWorld(pixInCell);
    }

    @Override
    public MathTransform getOriginalGridToWorld(String coverageName, PixelInCell pixInCell) {
        return delegate.getOriginalGridToWorld(coverageName, pixInCell);
    }

    @Override
    public Set<ParameterDescriptor<List>> getDynamicParameters() throws IOException {
        return delegate.getDynamicParameters();
    }

    @Override
    public Set<ParameterDescriptor<List>> getDynamicParameters(String coverageName)
            throws IOException {
        return delegate.getDynamicParameters(coverageName);
    }

    @Override
    public double[] getReadingResolutions(OverviewPolicy policy, double[] requestedResolution)
            throws IOException {
        return delegate.getReadingResolutions(policy, requestedResolution);
    }

    @Override
    public double[] getReadingResolutions(String coverageName, OverviewPolicy policy,
            double[] requestedResolution) throws IOException {
        return delegate.getReadingResolutions(coverageName, policy, requestedResolution);
    }

    @Override
    public int getNumOverviews() {
        return delegate.getNumOverviews();
    }

    @Override
    public int getNumOverviews(String coverageName) {
        return delegate.getNumOverviews(coverageName);
    }

    @Override
    public ImageLayout getImageLayout() throws IOException {
        return delegate.getImageLayout();
    }

    @Override
    public ImageLayout getImageLayout(String coverageName) throws IOException {
        return delegate.getImageLayout(coverageName);
    }

    @Override
    public double[][] getResolutionLevels() throws IOException {
        return delegate.getResolutionLevels();
    }

    @Override
    public double[][] getResolutionLevels(String coverageName) throws IOException {
        return delegate.getResolutionLevels(coverageName);
    }

    @Override
    public GridCoverage2D read(GeneralParameterValue[] parameters) throws IOException {
        return read(null, parameters);
    }

    @Override
    public GridCoverage2D read(String coverageName, GeneralParameterValue[] parameters)
            throws IOException {

        ConveyorTile ct;
        try {
            final StorageBroker storageBroker = cache.getStorageBroker();
            // final GridSetBroker gridsetBroker = cache.getGridSetBroker();

            // Getting requested gridGeometry and envelope
            final GridGeometry2D gridGeometry = extractEnvelope(parameters);
            Envelope requestedEnvelope = null;
            if (gridGeometry != null) {
                requestedEnvelope = gridGeometry.getEnvelope();
            }
            if (requestedEnvelope == null) {
                requestedEnvelope = getOriginalEnvelope(coverageName);
            }

            ReferencedEnvelope env = new ReferencedEnvelope(requestedEnvelope);
            BoundingBox bbox = new BoundingBox(env.getMinX(), env.getMinY(), env.getMaxX(),
                    env.getMaxY());

            // Finding tiles involved by the request
            //long[] tiles = gridSet.closestRectangle(bbox);
            GridEnvelope2D gridEnv = null;
            if(gridGeometry != null){
                gridEnv = gridGeometry.getGridRange2D();
            }
            
            if(gridEnv == null){
                gridEnv = (GridEnvelope2D) getOriginalGridRange();
            }

            Integer zoomLevel = findClosestZoom(gridSet, env, gridEnv.width);

            long[] tiles = gridSubSet.get(0).getCoverageIntersection(zoomLevel, bbox);// TODO CHANGE HERE

            final int minX = (int) tiles[0];
            final int minY = (int) tiles[1];
            final int maxX = (int) tiles[2];
            final int maxY = (int) tiles[3];
            final int level = (int) tiles[4];
            final int wTiles = maxX - minX + 1;
            final int hTiles = maxY - minY + 1;
            final int tileHeight = gridSet.getTileHeight();
            final int tileWidth = gridSet.getTileWidth();
            Map<String, ConveyorTile> cTiles = new HashMap<String, ConveyorTile>();

            String id = wcsLayer.getName();
            String name = GridCoveragesCache.REFERENCE.getName();
            
            Map<String,String> filteringParameters = extractParameters(parameters);

            // // 
            // Getting tiles
            // //
            for (int i = minX; i <= maxX; i++) {
                for (int j = minY; j <= maxY; j++) {
                    ct = new ConveyorTile(storageBroker, id, name, new long[] { i, j, level },
                            TIFF_MIME_TYPE, filteringParameters, null, null);
                    try {
                        ConveyorTile tile = wcsLayer.getTile(ct);
                        // Ask again for the Tile in order to get the cached 
                        // one with FileResource and not the Conveyor Tile 
                        // with the ByteArrayResource
                        tile = wcsLayer.getTile(ct);
                        String index = i + "_" + j;
                        cTiles.put(index, tile);
                    } catch (OutsideCoverageException oce) {
                        if (LOGGER.isLoggable(Level.FINE)) {
                            LOGGER.fine("Exception occurred while getting tile (" + i + "," + j
                                    + ") due to " + oce);
                        }
                    }
                }
            }
            
            // //
            // Reassembling tiles
            // //
            GridSubset subset = wcsLayer.getGridSubset(gridSet.getName());
            ImageLayout layout = new ImageLayout2(minX,
                    minY, tileWidth * wTiles, tileHeight * hTiles, 0, 0, tileWidth, tileHeight, null, null);
            
            // setup tile set to satisfy the request

            CoordinateReferenceSystem crs = requestedEnvelope.getCoordinateReferenceSystem();
            ConveyorTilesRenderedImage finalImage = new ConveyorTilesRenderedImage(cTiles, layout, axisOrderingTopDown, wTiles, hTiles, zoomLevel, gridSet, subset, crs);
            ReferencedEnvelope readEnvelope = finalImage.getEnvelope();
            GridCoverage2D readCoverage = gcf.create(name, finalImage, readEnvelope);
            return readCoverage;
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    private Map<String, String> extractParameters(GeneralParameterValue[] parameters) {
        Map<String, String> params = null;
        for (GeneralParameterValue gParam : parameters) {
            GeneralParameterDescriptor descriptor = gParam.getDescriptor();
            final ReferenceIdentifier name = descriptor.getName();
            if (name.equals(AbstractGridFormat.TIME.getName())) {
                if (gParam instanceof ParameterValue<?>) {
                    final ParameterValue<?> param = (ParameterValue<?>) gParam;
                    final Object value = param.getValue();
                    List<Date> times = (List<Date>)value;
                    Date date = times.get(0);
                    if (params == null) {
                        params = new HashMap<String, String>();
                    } 
                    params.put(WCSSourceHelper.TIME, formatter.format(date));
                }
            } else if (name.equals(AbstractGridFormat.ELEVATION.getName())) {
                if (gParam instanceof ParameterValue<?>) {
                    final ParameterValue<?> param = (ParameterValue<?>) gParam;
                    final Object value = param.getValue();
                    List<Number> elevations = (List<Number>)value;
                    Number elevation = elevations.get(0);
                    if (params == null) {
                        params = new HashMap<String, String>();
                    }
                    params.put(WCSSourceHelper.ELEVATION, elevation.toString());
                }
            }
            // TODO: ADD management for custom dimensions
        }
        return params;
    }

    /**
     * This method checks if the Gridset Y axis order increases from top to bottom.
     * 
     * @param gridSet
     * @return
     */
    private boolean axisOrderingTopDown() {
        int level = 2;
        GridSubset subset = GridSubsetFactory.createGridSubSet(gridSet, gridSet.getOriginalExtent(), level, level);
        BoundingBox b1 = subset.boundsFromIndex(new long[]{0 , 0, level});
        BoundingBox b2 = subset.boundsFromIndex(new long[]{0 , 1, level});
        return b2.getMinX() < b1.getMinX();
    }

    /**
     * This method returns the closest zoom level for the requested BBOX and resolution
     * 
     * @param gridSet
     * @param env
     * @param width
     * @return closest zoom level to the requested resolution
     */
    private Integer findClosestZoom(GridSet gridSet, ReferencedEnvelope env, int width) {
        double reqScale = RendererUtilities.calculateOGCScale(env, width,
                null);

        int i = 0;
        double error = Math.abs(gridSet.getGrid(i).getScaleDenominator() - reqScale);
        while (i < gridSet.getNumLevels() - 1) {
            Grid g = gridSet.getGrid(i + 1);
            double e = Math.abs(g.getScaleDenominator() - reqScale);

            if (e > error) {
                break;
            } else {
                error = e;
            }
            i++;
        }

        return Math.max(i, 0);
    }


    /**
     * Extract the reading envelope from the parameter list.
     * 
     * @param coverageName
     * @param parameters
     * @return
     */
    private GridGeometry2D extractEnvelope(GeneralParameterValue[] parameters) {
        for (GeneralParameterValue gParam : parameters) {
            GeneralParameterDescriptor descriptor = gParam.getDescriptor();
            final ReferenceIdentifier name = descriptor.getName();
            if (name.equals(AbstractGridFormat.READ_GRIDGEOMETRY2D.getName())) {
                if (gParam instanceof ParameterValue<?>) {
                    final ParameterValue<?> param = (ParameterValue<?>) gParam;
                    final Object value = param.getValue();
                    final GridGeometry2D gg = (GridGeometry2D) value;
                    return gg;
                }
            }
        }
        return null;
    }
    
    /**
     * Caching Structured GridCoverage2DReader implementation
     */
    static class CachingStructuredGridCoverage2DReader extends CachingGridCoverage2DReader implements StructuredGridCoverage2DReader {

        private StructuredGridCoverage2DReader structuredDelegate;
        
        public CachingStructuredGridCoverage2DReader( GridCoveragesCache cache,
                CoverageInfo info, StructuredGridCoverage2DReader reader) {
            super(cache, info, reader);
            this.structuredDelegate = reader;
        }

        @Override
        public GranuleSource getGranules(String coverageName, boolean readOnly) throws IOException,
                UnsupportedOperationException {
            return structuredDelegate.getGranules(coverageName, readOnly);
        }

        @Override
        public boolean isReadOnly() {
            return structuredDelegate.isReadOnly();
        }

        @Override
        public void createCoverage(String coverageName, SimpleFeatureType schema)
                throws IOException, UnsupportedOperationException {
            structuredDelegate.createCoverage(coverageName, schema);
        }

        @Override
        public boolean removeCoverage(String coverageName) throws IOException,
                UnsupportedOperationException {
            return structuredDelegate.removeCoverage(coverageName);
        }

        @Override
        public boolean removeCoverage(String coverageName, boolean delete) throws IOException,
                UnsupportedOperationException {
            return structuredDelegate.removeCoverage(coverageName, delete);
        }

        @Override
        public void delete(boolean deleteData) throws IOException {
            structuredDelegate.delete(deleteData);
        }

        @Override
        public List<HarvestedSource> harvest(String defaultTargetCoverage, Object source,
                Hints hints) throws IOException, UnsupportedOperationException {
            return structuredDelegate.harvest(defaultTargetCoverage, source, hints);
        }

        @Override
        public List<DimensionDescriptor> getDimensionDescriptors(String coverageName)
                throws IOException {
            return structuredDelegate.getDimensionDescriptors(coverageName);
        }
        
    }
}

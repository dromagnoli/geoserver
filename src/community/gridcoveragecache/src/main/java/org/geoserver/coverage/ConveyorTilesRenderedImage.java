/* (c) 2014 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.coverage;

import java.awt.Rectangle;
import java.awt.image.ColorModel;
import java.awt.image.Raster;
import java.awt.image.RenderedImage;
import java.awt.image.SampleModel;
import java.awt.image.WritableRaster;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.media.jai.ImageLayout;
import javax.media.jai.Interpolation;
import javax.media.jai.RenderedOp;
import javax.media.jai.operator.TranslateDescriptor;

import org.geoserver.coverage.layer.CoverageMetaTile;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.referencing.CRS;
import org.geowebcache.conveyor.ConveyorTile;
import org.geowebcache.grid.BoundingBox;
import org.geowebcache.grid.GridSet;
import org.geowebcache.grid.GridSubset;
import org.geowebcache.grid.GridSubsetFactory;
import org.jaitools.imageutils.ImageLayout2;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.NoSuchAuthorityCodeException;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

public class ConveyorTilesRenderedImage implements RenderedImage {

    private final static Logger LOGGER = org.geotools.util.logging.Logging
            .getLogger(ConveyorTilesRenderedImage.class);

    private int tileWidth;

    private int tileHeight;

    private int minX;

    private int minY;

    private ReferencedEnvelope envelope;

    private int width;

    private int height;

    private int numXTiles;

    private int numYTiles;

    private SampleModel sampleModel;

    private ColorModel colorModel;

    private Map<String, ConveyorTile> cTiles;

    private int cTileStartX;

    private long cTileStartY;

    private final boolean axisOrderingTopDown;

    public ReferencedEnvelope getEnvelope() {
        return envelope;
    }

    public ConveyorTilesRenderedImage(Map<String, ConveyorTile> cTiles, ImageLayout layout,
            boolean axisOrderingTopDown, int wTiles, int hTiles, Integer zoomLevel,
            GridSet gridSet, GridSubset subset, CoordinateReferenceSystem crs) throws IOException {

        // Value used for defining the Mosaicked image Y origin
        long minYImage = Integer.MAX_VALUE;
        // Maximum Y value for the gridset at the level defined by the zoomlevel variable
        long maxYTotal = gridSet.getGrid(zoomLevel).getNumTilesHigh() - 1;

        // Setting axis ordering
        this.axisOrderingTopDown = axisOrderingTopDown;

        if (axisOrderingTopDown) {
            minYImage = 0;
        }
        final int localMinX = layout.getMinX(null);
        final int localMinY = layout.getMinY(null);
        this.sampleModel = layout.getSampleModel(null);
        long currentMinY = minYImage;

        tileWidth = layout.getTileWidth(null);
        tileHeight = layout.getTileHeight(null);

        BoundingBox extent = null;
        double minBBX = Double.POSITIVE_INFINITY;
        double minBBY = Double.POSITIVE_INFINITY;
        double maxBBX = Double.NEGATIVE_INFINITY;
        double maxBBY = Double.NEGATIVE_INFINITY;
        Set<String> keys = cTiles.keySet();

        this.cTiles = cTiles;
        ConveyorTile tile = null;

        // Setting up the boundingBox
        for (String key : keys) {
            tile = cTiles.get(key);
            long[] tileIndex = tile.getTileIndex();
            extent = subset.boundsFromIndex(tileIndex);
            minBBX = Math.min(minBBX, extent.getMinX());
            minBBY = Math.min(minBBY, extent.getMinY());
            maxBBX = Math.max(maxBBX, extent.getMaxX());
            maxBBY = Math.max(maxBBY, extent.getMaxY());
            currentMinY = (axisOrderingTopDown ? tileIndex[1] : (maxYTotal - tileIndex[1]));
            if (!axisOrderingTopDown && currentMinY < minYImage) {
                minYImage = currentMinY;
            }
        }
        envelope = new ReferencedEnvelope(minBBX, maxBBX, minBBY, maxBBY, crs);

        // Preparing the ImageLayout
        ImageLayout layout2 = new ImageLayout2(localMinX * tileWidth,
                (int) (axisOrderingTopDown ? localMinY : minYImage) * tileHeight, tileWidth
                        * wTiles, tileHeight * hTiles);
        cTileStartX = localMinX;
        cTileStartY = !axisOrderingTopDown ? localMinY : minYImage; 

        minX = layout2.getMinX(null);
        minY = layout2.getMinY(null);
        width = layout2.getWidth(null);
        height = layout2.getHeight(null);
        numXTiles = width / tileWidth;
        numYTiles = height / tileHeight;
    }

    public ConveyorTilesRenderedImage(Map<String, ConveyorTile> cTiles, GridSet gridSet,
            GridSubset subset, ImageLayout layout) throws NoSuchAuthorityCodeException,
            IOException, FactoryException {
        this(cTiles, extractLayout(cTiles, gridSet, layout), axisOrderingTopDown(gridSet), 2, 2,
                (int) extractZoomeLevel(cTiles), gridSet, subset, CRS.decode("EPSG:"
                        + gridSet.getSrs().getNumber()));
    }

    @Override
    public Vector<RenderedImage> getSources() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Object getProperty(String name) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public String[] getPropertyNames() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public ColorModel getColorModel() {
        return colorModel;
    }

    @Override
    public SampleModel getSampleModel() {
        return sampleModel;
    }

    @Override
    public int getWidth() {
        return width;
    }

    @Override
    public int getHeight() {
        return height;
    }

    @Override
    public int getMinX() {
        return minX;
    }

    @Override
    public int getMinY() {
        return minY;
    }

    @Override
    public int getNumXTiles() {
        return numXTiles;
    }

    @Override
    public int getNumYTiles() {
        return numYTiles;
    }

    @Override
    public int getMinTileX() {
        return 0;
    }

    @Override
    public int getMinTileY() {
        return 0;
    }

    @Override
    public int getTileWidth() {
        return tileWidth;
    }

    @Override
    public int getTileHeight() {
        return tileHeight;
    }

    @Override
    public int getTileGridXOffset() {
        return minX;
    }

    @Override
    public int getTileGridYOffset() {
        return minY;
    }

    @Override
    public Raster getTile(int tileX, int tileY) {
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.fine("Getting tile: " + tileX + " , " + tileY);
        }
        final int x = (int) (cTileStartX + tileX);
        final int y = (int) (cTileStartY + (axisOrderingTopDown ? tileY : numYTiles - 1 - tileY));
        ConveyorTile tile = cTiles.get(x + "_" + y);
        try {
            RenderedImage resource = CoverageMetaTile.getResource(tile);
            float xTrans = minX + tileX * getTileWidth();
            float yTrans = minY + tileY * getTileHeight();
            RenderedOp result = TranslateDescriptor.create(resource, xTrans, yTrans,
                    Interpolation.getInstance(Interpolation.INTERP_NEAREST), null);
            return result.getData();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Raster getData() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Raster getData(Rectangle rect) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public WritableRaster copyData(WritableRaster raster) {
        // TODO Auto-generated method stub
        return null;
    }
    
    /**
     * This method checks if the Gridset Y axis order increases from top to bottom.
     * 
     * @param gridSet
    * @return
     */
    private static boolean axisOrderingTopDown(GridSet gridSet) {
        int level = 2;
        GridSubset subset = GridSubsetFactory.createGridSubSet(gridSet,
                gridSet.getOriginalExtent(), level, level);
        BoundingBox b1 = subset.boundsFromIndex(new long[] { 0, 0, level });
        BoundingBox b2 = subset.boundsFromIndex(new long[] { 0, 1, level });
        return b2.getMinX() < b1.getMinX();
    }

    private static long extractZoomeLevel(Map<String, ConveyorTile> cTiles) {
        String key = cTiles.keySet().iterator().next();
        ConveyorTile tile = cTiles.get(key);
        return tile.getTileIndex()[2];
    }

    private static ImageLayout extractLayout(Map<String, ConveyorTile> cTiles, GridSet gridSet,
            ImageLayout input) {

        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        for (String key : cTiles.keySet()) {
            ConveyorTile tile = cTiles.get(key);
            long[] tileIndex = tile.getTileIndex();
            if (tileIndex[0] < minX) {
                minX = (int) tileIndex[0];
            }
            if (tileIndex[1] < minY) {
                minY = (int) tileIndex[1];
            }
        }

        final int wTiles = 2;
        final int hTiles = 2;
        final int tileHeight = gridSet.getTileHeight();
        final int tileWidth = gridSet.getTileWidth();

        ImageLayout layout = new ImageLayout2(minX, minY, tileWidth * wTiles, tileHeight * hTiles,
                0, 0, tileWidth, tileHeight, input.getSampleModel(null), null);

        return layout;
    }
}

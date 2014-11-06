/* (c) 2014 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.coverage;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import javax.media.jai.ImageLayout;
import javax.media.jai.Interpolation;

import org.geoserver.catalog.CoverageInfo;
import org.geoserver.catalog.CoverageStoreInfo;
import org.geoserver.catalog.LayerInfo;
import org.geoserver.catalog.ResourcePool;
import org.geoserver.catalog.impl.LayerGroupInfoImpl;
import org.geoserver.gwc.GWC;
import org.geoserver.gwc.config.GWCConfig;
import org.geoserver.gwc.layer.GeoServerTileLayer;
import org.geoserver.gwc.layer.GeoServerTileLayerInfo;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.conveyor.ConveyorTile;
import org.geowebcache.grid.GridSetBroker;
import org.geowebcache.grid.GridSubset;
import org.geowebcache.grid.OutsideCoverageException;
import org.geowebcache.locks.LockProvider.Lock;
import org.geowebcache.mime.FormatModifier;
import org.geowebcache.mime.MimeType;
import org.geowebcache.util.GWCVars;

/**
 * A tile layer backed by a WCS server
 */
public class WCSLayer extends GeoServerTileLayer {

    private final static Logger LOGGER = org.geotools.util.logging.Logging
            .getLogger(WCSLayer.class);

    private transient WCSSourceHelper sourceHelper;

    protected String name;

    protected Map<String, GridSubset> subSets;

    /** Interpolation used when processing raster data to populate this layer's tiles **/
    protected Interpolation interpolation = Interpolation.getInstance(Interpolation.INTERP_NEAREST); 

    private ImageLayout layout;

    private String workspaceName;

    private ReferencedEnvelope bbox;

    private String coverageName;

    public WCSLayer(CoverageInfo info, GridSetBroker broker, List<GridSubset> gridSubsets,
            ImageLayout layout, GeoServerTileLayerInfo state) throws Exception {
        super(new LayerGroupInfoImpl(), broker, state);

        subSets = new HashMap<String, GridSubset>();
        for(GridSubset gridSubset : gridSubsets){
            subSets.put(gridSubset.getName(), gridSubset);
        }

        final CoverageStoreInfo storeInfo = info.getStore();
        workspaceName = storeInfo.getWorkspace().getName();
        coverageName = info.getName();
        name = workspaceName + ":" + coverageName;
        bbox = info.boundingBox();
        sourceHelper = new WCSSourceHelper(this);
        GeoServerTileLayerInfo localLayerInfo = getInfo();
        localLayerInfo.setName(name + "wcsLayerInfo");
        localLayerInfo.getMimeFormats().add("image/tiff");
        this.layout = layout;
        
        //TODO: Customize Interpolation, getting it from the GUI
    }

    @Override
    public Set<String> getGridSubsets() {
        return Collections.unmodifiableSet(this.subSets.keySet());
    }

    @Override
    public GridSubset getGridSubset(String gridSetId) {
        return subSets.get(gridSetId);
    }

    public String getCoverageName() {
        return coverageName;
    }

    public String getWorkspaceName() {
        return workspaceName;
    }

    public ReferencedEnvelope getBbox() {
        return bbox;
    }

    /**
     * Used for seeding
     */
    public void seedTile(ConveyorTile tile, boolean tryCache) throws GeoWebCacheException,
            IOException {
        GridSubset gridSubset = getGridSubset(tile.getGridSetId());
        if (gridSubset.shouldCacheAtZoom(tile.getTileIndex()[2])) {
            // Always use metaTiling on seeding since we are implementing our
            // custom GWC layer
            getMetatilingReponse(tile, tryCache, 2, 2);
        }
    }

    private ConveyorTile getMetatilingReponse(ConveyorTile tile, final boolean tryCache,
            final int metaX, final int metaY) throws GeoWebCacheException, IOException {

        final GridSubset gridSubset = getGridSubset(tile.getGridSetId());
        final int zLevel = (int) tile.getTileIndex()[2];
        tile.setMetaTileCacheOnly(!gridSubset.shouldCacheAtZoom(zLevel));

        if (tryCache && tryCacheFetch(tile)) {
            return finalizeTile(tile);
        }

        final WCSMetaTile metaTile = createMetaTile(tile, metaX, metaY);
        Lock lock = null;
        try {
            /** ****************** Acquire lock ******************* */
            lock = GWC.get().getLockProvider().getLock(buildLockKey(tile, metaTile));
            // got the lock on the meta tile, try again
            if (tryCache && tryCacheFetch(tile)) {
                LOGGER.finest("--> " + Thread.currentThread().getName() + " returns cache hit for "
                        + Arrays.toString(metaTile.getMetaGridPos()));
            } else {
                LOGGER.finer("--> " + Thread.currentThread().getName()
                        + " submitting request for meta grid location "
                        + Arrays.toString(metaTile.getMetaGridPos()) + " on " + metaTile);
                try {
                    long requestTime = System.currentTimeMillis();

                    sourceHelper.makeRequest(metaTile, tile, interpolation);
                    saveTiles(metaTile, tile, requestTime);
                } catch (Exception e) {
                    throw new GeoWebCacheException("Problem communicating with GeoServer", e);
                }
            }
            /** ****************** Return lock and response ****** */
        } finally {
            if (lock != null) {
                lock.release();
            }
            metaTile.dispose();
        }
        return finalizeTile(tile);
    }

    private String buildLockKey(ConveyorTile tile, WCSMetaTile metaTile) {
        StringBuilder metaKey = new StringBuilder();

        final long[] tileIndex;
        if (metaTile != null) {
            tileIndex = metaTile.getMetaGridPos();
            metaKey.append("meta_");
        } else {
            tileIndex = tile.getTileIndex();
            metaKey.append("tile_");
        }
        long x = tileIndex[0];
        long y = tileIndex[1];
        long z = tileIndex[2];

        metaKey.append(tile.getLayerId());
        metaKey.append("_").append(tile.getGridSetId());
        metaKey.append("_").append(x).append("_").append(y).append("_").append(z);
        if (tile.getParametersId() != null) {
            metaKey.append("_").append(tile.getParametersId());
        }
        metaKey.append(".").append(tile.getMimeType().getFileExtension());

        return metaKey.toString();
    }

    public boolean tryCacheFetch(ConveyorTile tile) {
        int expireCache = this.getExpireCache((int) tile.getTileIndex()[2]);
        if (expireCache != GWCVars.CACHE_DISABLE_CACHE) {
            try {
                return tile.retrieve(expireCache * 1000L);
            } catch (GeoWebCacheException gwce) {
                LOGGER.severe(gwce.getMessage());
                tile.setErrorMsg(gwce.getMessage());
                return false;
            }
        }
        return false;
    }

    public ConveyorTile doNonMetatilingRequest(ConveyorTile tile) throws GeoWebCacheException {
        // We are doing our custom GWC layer implementation for gridCoverage setup
        throw new UnsupportedOperationException();
    }

    public ConveyorTile getNoncachedTile(ConveyorTile tile) throws GeoWebCacheException {
        // We are doing our custom GWC layer implementation for gridCoverage setup
        throw new UnsupportedOperationException();
    }


    private ConveyorTile finalizeTile(ConveyorTile tile) {
        if (tile.getStatus() == 0 && !tile.getError()) {
            tile.setStatus(200);
        }

        if (tile.servletResp != null) {
            setExpirationHeader(tile.servletResp, (int) tile.getTileIndex()[2]);
        }

        return tile;
    }


    public long[][] getZoomedInGridLoc(String gridSetId, long[] gridLoc)
            throws GeoWebCacheException {
        return null;
    }

//    public void addMetaWidthHeight(int w, int h) {
//        this.metaWidthHeight[0] = w;
//        this.metaWidthHeight[1] = h;
//    }

    public void setSourceHelper(WCSSourceHelper source) {
        LOGGER.fine("Setting sourceHelper on " + this.name);
        this.sourceHelper = source;

    }


    // public void cleanUpThreadLocals() {
    // WMS_BUFFER.remove();
    // WMS_BUFFER2.remove();
    // }

    @Override
    public String getStyles() {
        // Styles are ignored
        return null;
    }

    @Override
    public ConveyorTile getTile(ConveyorTile tile) throws GeoWebCacheException, IOException,
            OutsideCoverageException {
        MimeType mime = tile.getMimeType();
        final List<MimeType> formats = getMimeTypes();
        if (mime == null) {
            mime = formats.get(0);
        } else {
            if (!formats.contains(mime)) {
                throw new IllegalArgumentException(mime.getFormat()
                        + " is not a supported format for " + getName());
            }
        }

        final String tileGridSetId = tile.getGridSetId();
        final GridSubset gridSubset = getGridSubset(tileGridSetId);
        if (gridSubset == null) {
            throw new IllegalArgumentException("Requested gridset not found: " + tileGridSetId);
        }

        final long[] gridLoc = tile.getTileIndex();
        checkNotNull(gridLoc);

        // Final preflight check, throws OutsideCoverageException if necessary
        gridSubset.checkCoverage(gridLoc);

        ConveyorTile returnTile;

        // Customize metatiling size
        final int metaX = 2;
        final int metaY = 2;

        returnTile = getMetatilingReponse(tile, true, metaX, metaY);

        sendTileRequestedEvent(returnTile);

        return returnTile;
    }

    private WCSMetaTile createMetaTile(ConveyorTile tile, final int metaX, final int metaY) {
        WCSMetaTile metaTile;

        final String tileGridSetId = tile.getGridSetId();
        final GridSubset gridSubset = getGridSubset(tileGridSetId);
        final MimeType responseFormat = tile.getMimeType();
        FormatModifier formatModifier = null;
        long[] tileGridPosition = tile.getTileIndex();
        metaTile = new WCSMetaTile(this, gridSubset, responseFormat, formatModifier,
                tileGridPosition, metaX, metaY, tile.getFullParameters());

        return metaTile;
    }

    public ImageLayout getLayout() {
        return layout;
    }
    
    @Override
    public boolean isAdvertised() {
        return false;
    }

    @Override
    public void setAdvertised(boolean advertised) {
        return;
    }
}

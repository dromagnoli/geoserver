/* (c) 2014 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.coverage.layer;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.media.jai.ImageLayout;
import javax.media.jai.Interpolation;

import org.geoserver.catalog.CoverageInfo;
import org.geoserver.catalog.CoverageStoreInfo;
import org.geoserver.catalog.impl.LayerGroupInfoImpl;
import org.geoserver.coverage.WCSSourceHelper;
import org.geoserver.coverage.configuration.CoverageConfiguration;
import org.geoserver.gwc.GWC;
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
public class CoverageTileLayer extends GeoServerTileLayer {

    private final static Logger LOGGER = org.geotools.util.logging.Logging
            .getLogger(CoverageTileLayer.class);
    
    private transient WCSSourceHelper sourceHelper;

    protected String name;

    protected Map<String, GridSubset> subSets;

    private ImageLayout layout;

    private String workspaceName;

    private ReferencedEnvelope bbox;

    private String coverageName;
    
    Interpolation interpolation = Interpolation.getInstance(Interpolation.INTERP_NEAREST);

    private CoverageTileLayerInfo coverageTileLayerInfo; 

    public CoverageTileLayer(CoverageInfo info, GridSetBroker broker, List<GridSubset> gridSubsets,
            GeoServerTileLayerInfo state, boolean init) throws Exception {
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
        
        GeoServerTileLayerInfo localInfo = super.getInfo();
        if(localInfo instanceof CoverageTileLayerInfo){
            this.coverageTileLayerInfo = (CoverageTileLayerInfo) localInfo;
        } else {
            this.coverageTileLayerInfo = new CoverageTileLayerInfoImpl(localInfo);
        }
        if (init) {
            coverageTileLayerInfo.setId(info.getId());
            coverageTileLayerInfo.setName(name + CoverageConfiguration.COVERAGE_LAYER_SUFFIX);
            coverageTileLayerInfo.getMimeFormats().add("image/tiff");
        }
        
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
    
    @Override
    public GeoServerTileLayerInfo getInfo() {
       return coverageTileLayerInfo;
    }

    public void setLayout(ImageLayout layout) {
        this.layout = layout;
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
            getMetatilingReponse(tile, tryCache, coverageTileLayerInfo.getMetaTilingX(), coverageTileLayerInfo.getMetaTilingY());
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

        final CoverageMetaTile metaTile = createMetaTile(tile, metaX, metaY);
        Lock lock = null;
        try {
            /** ****************** Acquire lock ******************* */
            lock = GWC.get().getLockProvider().getLock(buildLockKey(tile, metaTile));
            // got the lock on the meta tile, try again
            if (tryCache && tryCacheFetch(tile)) {
                if (LOGGER.isLoggable(Level.FINEST)) {
                LOGGER.finest("--> " + Thread.currentThread().getName() + " returns cache hit for "
                        + Arrays.toString(metaTile.getMetaGridPos()));
                }
            } else {
                if (LOGGER.isLoggable(Level.FINER)) {
                LOGGER.finer("--> " + Thread.currentThread().getName()
                        + " submitting request for meta grid location "
                        + Arrays.toString(metaTile.getMetaGridPos()) + " on " + metaTile);
                }
                try {
                    long requestTime = System.currentTimeMillis();

                    sourceHelper.makeRequest(metaTile, tile, interpolation
                            /*coverageTileLayerInfo.getResamplingAlgorithm()*/);
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

    private String buildLockKey(ConveyorTile tile, CoverageMetaTile metaTile) {
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

    public void setSourceHelper(WCSSourceHelper source) {
        LOGGER.fine("Setting sourceHelper on " + this.name);
        this.sourceHelper = source;

    }


    public void cleanUpThreadLocals() {
        WMS_BUFFER.remove();
        WMS_BUFFER2.remove();
    }

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

        try {
            returnTile = getMetatilingReponse(tile, true, coverageTileLayerInfo.getMetaTilingX(), 
                    coverageTileLayerInfo.getMetaTilingY());
        } finally {
            cleanUpThreadLocals();
        }

        sendTileRequestedEvent(returnTile);

        return returnTile;
    }

    private CoverageMetaTile createMetaTile(ConveyorTile tile, final int metaX, final int metaY) {
        CoverageMetaTile metaTile;

        final String tileGridSetId = tile.getGridSetId();
        final GridSubset gridSubset = getGridSubset(tileGridSetId);
        final MimeType responseFormat = tile.getMimeType();
        FormatModifier formatModifier = null;
        long[] tileGridPosition = tile.getTileIndex();
        metaTile = new CoverageMetaTile(this, gridSubset, responseFormat, formatModifier,
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

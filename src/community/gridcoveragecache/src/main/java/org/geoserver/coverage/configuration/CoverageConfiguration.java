package org.geoserver.coverage.configuration;

import static com.google.common.base.Preconditions.checkNotNull;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReadWriteLock;

import org.geoserver.catalog.Catalog;
import org.geoserver.catalog.CoverageInfo;
import org.geoserver.catalog.LayerGroupInfo;
import org.geoserver.catalog.LayerInfo;
import org.geoserver.coverage.WCSLayer;
import org.geoserver.gwc.layer.CatalogConfiguration;
import org.geoserver.gwc.layer.GeoServerTileLayer;
import org.geoserver.gwc.layer.GeoServerTileLayerInfo;
import org.geoserver.gwc.layer.TileLayerCatalog;
import org.geowebcache.config.Configuration;
import org.geowebcache.grid.GridSetBroker;
import org.geowebcache.grid.GridSubset;
import org.geowebcache.grid.GridSubsetFactory;
import org.geowebcache.layer.TileLayer;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

public class CoverageConfiguration extends CatalogConfiguration implements Configuration {

    private GridSetBroker gwcGridSetBroker;

    private Catalog gsCatalog;


    public CoverageConfiguration(Catalog catalog, TileLayerCatalog tileLayerCatalog,
            GridSetBroker gridSetBroker) {
        super(catalog, tileLayerCatalog, gridSetBroker);
        this.gsCatalog = catalog;
        this.gwcGridSetBroker = gridSetBroker;

        ReadWriteLock lock = null;
        Set<String> pendingDeletes = null;
        Map<String, GeoServerTileLayerInfo> pendingModications = null;
        try {
            Field lockRef = super.getClass().getDeclaredField("lock");
            Field pendingDeletesRef = super.getClass().getDeclaredField("pendingDeletes");
            Field pendingModicationsRef = super.getClass().getDeclaredField("pendingModications");

            lockRef.setAccessible(true);
            pendingDeletesRef.setAccessible(true);
            pendingModicationsRef.setAccessible(true);

            lock = (ReadWriteLock) lockRef.get(CoverageConfiguration.this);
            pendingDeletes = (Set<String>) pendingDeletesRef.get(CoverageConfiguration.this);
            pendingModications = (Map<String, GeoServerTileLayerInfo>) pendingModicationsRef
                    .get(CoverageConfiguration.this);

        } catch (NoSuchFieldException | SecurityException | IllegalArgumentException
                | IllegalAccessException e) {
            throw new RuntimeException(e);

        }

        LoadingCache<String, GeoServerTileLayer> layerCache = CacheBuilder.newBuilder()//
                .concurrencyLevel(10)//
                .expireAfterAccess(10, TimeUnit.MINUTES)//
                .initialCapacity(10)//
                .maximumSize(100)
                //
                .build(new WCSTileLayerLoader(tileLayerCatalog, pendingDeletes, gridSetBroker,
                        catalog, pendingModications, lock));

        try {
            Field cache = super.getClass().getDeclaredField("layerCache");
            cache.setAccessible(true);
            cache.set(CoverageConfiguration.this, layerCache);
        } catch (IllegalArgumentException | IllegalAccessException | NoSuchFieldException
                | SecurityException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @return {@code true} only if {@code tl instanceof} {@link WCSLayer} .
     * @see org.geowebcache.config.Configuration#canSave(org.geowebcache.layer.TileLayer)
     */
    @Override
    public boolean canSave(TileLayer tl) {
        return tl instanceof WCSLayer;
    }

    @Override
    public GeoServerTileLayer getTileLayerById(final String layerId) {
        checkNotNull(layerId, "layer id is null");

        GeoServerTileLayer tileLayerById = super.getTileLayerById(layerId);
        GeoServerTileLayerInfo info = tileLayerById.getInfo();

        GridSubset gridSubSet = GridSubsetFactory.createGridSubSet(gwcGridSetBroker.getGridSets()
                .get(0));

        CoverageInfo coverageInfo = gsCatalog.getCoverageByName(info.getName());
        ;
        WCSLayer layer;
        try {
            layer = new WCSLayer(coverageInfo, gwcGridSetBroker, Arrays.asList(gridSubSet), null,
                    info);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return layer;
    }

    /**
     * {@link GeoServerTileLayer} cache loader
     * 
     */
    static class WCSTileLayerLoader extends CacheLoader<String, GeoServerTileLayer> {
        private final TileLayerCatalog tileLayerCatalog;

        private final GridSetBroker gridSetBroker;

        private final Catalog geoServerCatalog;

        private final Map<String, GeoServerTileLayerInfo> pendingModications;

        private final Set<String> pendingDeletes;

        private final ReadWriteLock lock;

        private WCSTileLayerLoader(TileLayerCatalog tileLayerCatalog, Set<String> pendingDeletes,
                GridSetBroker gridSetBroker, Catalog geoServerCatalog,
                Map<String, GeoServerTileLayerInfo> pendingModications, ReadWriteLock lock) {
            this.tileLayerCatalog = tileLayerCatalog;
            this.gridSetBroker = gridSetBroker;
            this.geoServerCatalog = geoServerCatalog;
            this.lock = lock;
            this.pendingDeletes = pendingDeletes;
            this.pendingModications = pendingModications;
        }

        @Override
        public GeoServerTileLayer load(String layerId) throws Exception {
            GeoServerTileLayer tileLayer = null;

            lock.readLock().lock();
            try {
                if (pendingDeletes.contains(layerId)) {
                    throw new IllegalArgumentException("Tile layer '" + layerId + "' was deleted.");
                }
                GeoServerTileLayerInfo tileLayerInfo = pendingModications.get(layerId);
                if (tileLayerInfo == null) {
                    tileLayerInfo = tileLayerCatalog.getLayerById(layerId);
                }
                if (tileLayerInfo == null) {
                    throw new IllegalArgumentException("GeoServerTileLayerInfo '" + layerId
                            + "' does not exist.");
                }
                // TODO CONTINUE FROM HERE
                LayerInfo layerInfo = geoServerCatalog.getLayer(layerId);
                if (layerInfo != null) {
                    tileLayer = new GeoServerTileLayer(layerInfo, gridSetBroker, tileLayerInfo);
                } else {
                    LayerGroupInfo lgi = geoServerCatalog.getLayerGroup(layerId);
                    if (lgi != null) {
                        tileLayer = new GeoServerTileLayer(lgi, gridSetBroker, tileLayerInfo);
                    }
                }
            } finally {
                lock.readLock().unlock();
            }
            if (null == tileLayer) {
                throw new IllegalArgumentException("GeoServer layer or layer group '" + layerId
                        + "' does not exist");
            }
            return tileLayer;
        }
    }
}

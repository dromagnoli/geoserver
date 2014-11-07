/* (c) 2014 Open Source Geospatial Foundation - all rights reserved
 * (c) 2001 - 2013 OpenPlans
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.gwc.web.layer;

import org.apache.wicket.model.IModel;
import org.geoserver.catalog.CoverageInfo;
import org.geoserver.catalog.LayerInfo;
import org.geoserver.catalog.MetadataMap;
import org.geoserver.catalog.ResourceInfo;
import org.geoserver.coverage.layer.CoverageTileLayer;
import org.geoserver.coverage.layer.CoverageTileLayerInfo;
import org.geoserver.coverage.layer.CoverageTileLayerInfoImpl;
import org.geoserver.gwc.GWC;
import org.geoserver.gwc.config.GWCConfig;
import org.geoserver.gwc.layer.GeoServerTileLayer;
import org.geoserver.gwc.layer.TileLayerInfoUtil;
import org.geoserver.web.data.resource.LayerEditTabPanelInfo;
import org.geowebcache.layer.TileLayer;

public class RasterEditCacheOptionsTabPanelInfo extends LayerEditTabPanelInfo {

    private static final long serialVersionUID = 7917940832781227130L;

    @Override
    public CoverageTileLayerInfoModel createOwnModel(final IModel<? extends ResourceInfo> resourceModel,
            final IModel<LayerInfo> layerModel, final boolean isNew) {

        final GWC mediator = GWC.get();

        LayerInfo layerInfo = layerModel.getObject();
        ResourceInfo resource = layerInfo.getResource();

        if (!(resource instanceof CoverageInfo)) {
            throw new IllegalArgumentException(
                    "This Layer is not related to a CoverageInfo resource");
        }

        CoverageTileLayerInfo tileLayerInfo;

        final GWCConfig defaultSettings = mediator.getConfig();

        CoverageTileLayer tileLayer = null;

        if (!isNew) {
            Iterable<? extends TileLayer> layers = mediator.getTileLayers();
            
            for(TileLayer layer : layers){
                if(layer instanceof CoverageTileLayer && layer.getId().equalsIgnoreCase(resource.getId())){
                    tileLayer = (CoverageTileLayer) layer;
                    break;
                }
            }
        }

        if (isNew || tileLayer == null || !(tileLayer instanceof CoverageTileLayer)) {

            // First check on the CoverageInfo Metadata Map
//            MetadataMap map = coverage.getMetadata();
//            if (map != null && map.containsKey(WCSLayer.WCSLAYERINFO_KEY)) {
//                WCSLayerInfo info = map.get(WCSLayer.WCSLAYERINFO_KEY, WCSLayerInfo.class);
//                tileLayerInfo = (WCSLayerInfo) info.clone();
//            } else {
                // Else create a new One from the default configuration
                /*
                 * Ensure a sane config for defaults, in case automatic cache of new layers is defined and the defaults is misconfigured
                 */
                // TODO MAKE SURE TO LOAD THE CONFIGURATION DEFAULTS
                tileLayerInfo = new CoverageTileLayerInfoImpl();

        } else {
            CoverageTileLayerInfo info = (CoverageTileLayerInfo) ((CoverageTileLayer) tileLayer).getInfo();
            tileLayerInfo = (CoverageTileLayerInfo) info.clone();
        }

        tileLayerInfo.setEnabled(true);
        final boolean initWithTileLayer = (isNew && defaultSettings.isCacheLayersByDefault())
                || tileLayer != null;

        if (!initWithTileLayer) {
            tileLayerInfo.setId(null);// indicate not to create the tile layer
        }

        return new CoverageTileLayerInfoModel(tileLayerInfo, isNew);
    }
}

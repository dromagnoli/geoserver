/* (c) 2014 Open Source Geospatial Foundation - all rights reserved
 * (c) 2001 - 2013 OpenPlans
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.coverage.web;

import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.ResourceModel;
import org.geoserver.catalog.LayerInfo;
import org.geoserver.gwc.layer.CatalogConfiguration;
import org.geoserver.gwc.layer.GeoServerTileLayerInfo;
import org.geoserver.web.data.resource.LayerEditTabPanel;

/**
*
 */
public class RasterCacheOptionsTabPanel extends LayerEditTabPanel {

    private static final long serialVersionUID = 1L;

    public RasterCacheOptionsTabPanel(String id, IModel<LayerInfo> layerModel,
            IModel<GeoServerTileLayerInfo> tileLayerModel) {
        super(id, tileLayerModel);

        if (CatalogConfiguration.isLayerExposable(layerModel.getObject())) {

        } else {
            add(new Label("tileLayerEditor", new ResourceModel("geometryLessLabel")));
        }
    }

    @Override
    public void save() {

    }
}

package org.geoserver.coverage.configuration;

import java.util.Arrays;

import org.geoserver.catalog.CatalogException;
import org.geoserver.catalog.CatalogInfo;
import org.geoserver.catalog.CoverageInfo;
import org.geoserver.catalog.LayerInfo;
import org.geoserver.catalog.ResourceInfo;
import org.geoserver.catalog.event.CatalogAddEvent;
import org.geoserver.catalog.event.CatalogListener;
import org.geoserver.catalog.event.CatalogModifyEvent;
import org.geoserver.catalog.event.CatalogPostModifyEvent;
import org.geoserver.catalog.event.CatalogRemoveEvent;
import org.geoserver.gwc.GWC;

public class CoverageListener implements CatalogListener {

    private final GWC mediator;

    //private final Catalog catalog;

    public CoverageListener(final GWC mediator) {
        this.mediator = mediator;
        //this.catalog = catalog;
    }
    
    @Override
    public void handleAddEvent(CatalogAddEvent event) throws CatalogException {
    }

    @Override
    public void handleRemoveEvent(CatalogRemoveEvent event) throws CatalogException {
        CatalogInfo obj = event.getSource();
        if (!(obj instanceof LayerInfo)) {
            return;
        }
        
        // Get the associated resource
        ResourceInfo resource = ((LayerInfo)obj).getResource();
        
        if(!(resource instanceof CoverageInfo)){
            return;
        }
        
        String prefixedName = resource.prefixedName();
        
        // Add the suffix to the prefixedName
        prefixedName = prefixedName + CoverageConfiguration.COVERAGE_LAYER_SUFFIX;
        
        //TileLayer tileLayerByName = mediator.getTileLayerByName(prefixedName);
        boolean tileLayerExists = mediator.tileLayerExists(prefixedName);
        if (!tileLayerExists) {
            return;
        } else {
            // notify the layer has been removed
            mediator.removeTileLayers(Arrays.asList(prefixedName));
        }
    }

    @Override
    public void handleModifyEvent(CatalogModifyEvent event) throws CatalogException {
    }

    @Override
    public void handlePostModifyEvent(CatalogPostModifyEvent event) throws CatalogException {
    }

    @Override
    public void reloaded() {
    }
}

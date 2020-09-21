package org.geoserver.web.data.store.raster;

import org.apache.wicket.markup.html.form.Form;

public class GeoTIFFRasterEditPanel extends CogRasterEditPanel {
    private static final String[] EXTENSIONS = new String[] {".tiff", ".tif"};

    public GeoTIFFRasterEditPanel(String componentId, Form storeEditForm) {
        super(componentId, storeEditForm, EXTENSIONS);
    }
}

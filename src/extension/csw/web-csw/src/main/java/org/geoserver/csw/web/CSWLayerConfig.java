/* (c) 2015 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.csw.web;

import org.apache.wicket.markup.html.form.CheckBox;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.PropertyModel;
import org.apache.wicket.validation.validator.MinimumValidator;
import org.geoserver.catalog.LayerInfo;
import org.geoserver.catalog.MetadataMap;
import org.geoserver.config.GeoServer;
import org.geoserver.csw.CSWInfo;
import org.geoserver.csw.DirectDownloadSettings;
import org.geoserver.platform.GeoServerExtensions;
import org.geoserver.web.publish.LayerConfigurationPanel;
import org.geoserver.web.util.MetadataMapModel;

/**
 * A configuration panel for CoverageInfo properties that related to CSW publication
 */
@SuppressWarnings("serial")
public class CSWLayerConfig extends LayerConfigurationPanel {

    protected final CheckBox directDownloadEnabled;

    protected final TextField<Integer> maxDownloadSize;

    public CSWLayerConfig(String id, IModel<LayerInfo> model) {
        super(id, model);

        PropertyModel<MetadataMap> settingsMap = new PropertyModel<MetadataMap>(model,
                "resource.metadata");
        MetadataMap map = settingsMap.getObject();

        DirectDownloadSettings settings = DirectDownloadSettings.getSettingsFromMetadata(map, null);
        if (settings == null) {
            map.getMap().put(DirectDownloadSettings.DIRECTDOWNLOAD_KEY, setDefaultSettings(
                    GeoServerExtensions.bean(GeoServer.class).getService(CSWInfo.class)));

        }
        IModel<DirectDownloadSettings> directDownloadModel = new MetadataMapModel(map,
                DirectDownloadSettings.DIRECTDOWNLOAD_KEY,
                DirectDownloadSettings.class);

        directDownloadEnabled = new CheckBox("directDownloadEnabled", new PropertyModel(directDownloadModel, "directDownloadEnabled"));
        add(directDownloadEnabled);

        maxDownloadSize = new TextField<Integer>("maxDownloadSize", new PropertyModel(
                directDownloadModel, "maxDownloadSize"));
        maxDownloadSize.add(new MinimumValidator(0l));
        add(maxDownloadSize);
    }

    /**
     * Get DefaultSettings from {@link CSWInfo} config or default value.
     * 
     * @param cswInfo
     * @return
     */
    private DirectDownloadSettings setDefaultSettings(CSWInfo info) {
        if (info != null) {
            MetadataMap serviceInfoMetadata = info.getMetadata();
            DirectDownloadSettings infoSettings = DirectDownloadSettings.getSettingsFromMetadata(
                    serviceInfoMetadata, null);
            // create a copy of the CSWInfo settings
            if (infoSettings != null) {
                return new DirectDownloadSettings(infoSettings);
            }
        }
        return new DirectDownloadSettings();

    }

}

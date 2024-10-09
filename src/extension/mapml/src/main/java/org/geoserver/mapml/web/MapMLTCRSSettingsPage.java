package org.geoserver.mapml.web;

import org.apache.wicket.markup.html.form.Button;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.model.CompoundPropertyModel;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.model.PropertyModel;
import org.geoserver.catalog.MetadataMap;
import org.geoserver.config.GeoServer;
import org.geoserver.config.GeoServerInfo;
import org.geoserver.config.SettingsInfo;
import org.geoserver.mapml.tcrs.TiledCRSConstants;
import org.geoserver.web.admin.ServerAdminPage;
import org.geoserver.web.wicket.LiveCollectionModel;

import java.util.List;
import java.util.Set;

public class MapMLTCRSSettingsPage extends ServerAdminPage {

    public MapMLTCRSSettingsPage() {

        IModel<GeoServer> geoserverModel = getGeoServerModel();

        Form<GeoServer> form = new Form<>("form", new CompoundPropertyModel<>(geoserverModel));
        add(form);

        SettingsInfo settings = getGeoServerApplication().getGeoServer().getSettings();
        Model<SettingsInfo> settingsModel = new Model<>(settings);
        /*MetadataMap metadata = getGeoServer().getGlobal().getSettings().getMetadata();

        if (metadata == null) {
            System.out.println("Metadata is null.");
        } else {
            System.out.println("Metadata loaded successfully.");
        }

        Set<String> keys = metadata.keySet();
        System.out.println("Available keys in metadata:");
        for (String key : keys) {
            System.out.println(key);
        }

        List<String> crsList = (List<String>) metadata.get(TiledCRSConstants.TCRS_METADATA_KEY);

        LiveCollectionModel<String, List<String>> liveCollectionModel = new LiveCollectionModel<>() {
            @Override
            public List<String> getObject() {
                return crsList; // Return the extracted CRS list
            }
        };
        /*Model<MetadataMap> metadataMapIModel = new Model<>(metadata);
        PropertyModel<Object> crsListModel = new PropertyModel<>(metadataMapIModel, TiledCRSConstants.TCRS_METADATA_KEY);
        Object obj = crsListModel.getObject();
        */


        MapMLTCRSSettingsPanel panel = new MapMLTCRSSettingsPanel("mapMLTCRS", settingsModel);
        form.add(panel);

        Button submit =
                new Button("submit") {

                    @Override
                    public void onSubmit() {
                        save(true);
                    }
                };
        form.add(submit);

        Button cancel =
                new Button("cancel") {

                    @Override
                    public void onSubmit() {
                        doReturn();
                    }
                };
        form.add(cancel);
    }

    private void save(boolean doReturn) {
        /*GeoServer gs = geoServerModel.getObject();
        GeoServerInfo global = gs.getGlobal();
        global.setJAI(jaiModel.getObject());
        gs.save(global);
        if (doReturn) doReturn();*/
    }
}

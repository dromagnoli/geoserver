package org.geoserver.mapml.web;

import org.apache.wicket.markup.html.form.Button;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.model.CompoundPropertyModel;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;
import org.geoserver.catalog.MetadataMap;
import org.geoserver.config.GeoServer;
import org.geoserver.config.GeoServerInfo;
import org.geoserver.config.SettingsInfo;
import org.geoserver.web.admin.ServerAdminPage;

public class MapMLTCRSSettingsPage extends ServerAdminPage {

    private final IModel<GeoServer> geoserverModel;
    Model<SettingsInfo> settingsModel;

    public MapMLTCRSSettingsPage() {

        geoserverModel = getGeoServerModel();

        Form<GeoServer> form = new Form<>("form", new CompoundPropertyModel<>(geoserverModel));
        add(form);

        SettingsInfo settings = getGeoServerApplication().getGeoServer().getSettings();
        settingsModel = new Model<>(settings);
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
        GeoServer gs = getGeoServer();
        GeoServerInfo settingsInfo = geoserverModel.getObject().getGlobal();
        gs.save(settingsInfo);
        //gs.save(settingsModel.getObject());
        if (doReturn) doReturn();
    }
}

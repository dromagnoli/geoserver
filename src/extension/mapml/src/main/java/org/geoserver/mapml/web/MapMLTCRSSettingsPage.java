package org.geoserver.mapml.web;

import org.apache.wicket.markup.html.form.Button;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.model.CompoundPropertyModel;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;
import org.geoserver.catalog.MetadataMap;
import org.geoserver.config.GeoServer;
import org.geoserver.config.GeoServerInfo;
import org.geoserver.mapml.tcrs.TiledCRSConstants;
import org.geoserver.mapml.tcrs.TiledCRSInfo;
import org.geoserver.web.admin.ServerAdminPage;
import org.geoserver.web.util.MetadataMapModel;

import java.util.List;

public class MapMLTCRSSettingsPage extends ServerAdminPage {

    public MapMLTCRSSettingsPage() {


        IModel<GeoServer> geoserverModel = getGeoServerModel();

        Form<GeoServer> form = new Form<>("form", new CompoundPropertyModel<>(geoserverModel));
        add(form);

//        IModel<GeoServerInfo> globalInfoModel = getGlobalInfoModel();
        MetadataMap metadata = getGeoServer().getGlobal().getSettings().getMetadata();


        /*
        MetadataMapModel<Object> metadataModel = new MetadataMapModel<>(
                metadata,
                TiledCRSConstants.TCRS_METADATA_KEY,
                List.class);
*/

        Model<MetadataMap> metadataModel = new Model<>(metadata);
        MapMLTCRSSettingsPanel panel = new MapMLTCRSSettingsPanel("mapMLTCRS", metadataModel );
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

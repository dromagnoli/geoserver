package org.geoserver.mapml.web;

import java.util.ArrayList;
import java.util.List;
import org.apache.wicket.Component;
import org.apache.wicket.extensions.markup.html.form.palette.Palette;
import org.apache.wicket.extensions.markup.html.form.palette.theme.DefaultTheme;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.ChoiceRenderer;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.model.AbstractReadOnlyModel;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.PropertyModel;
import org.apache.wicket.model.ResourceModel;
import org.geoserver.catalog.MetadataMap;
import org.geoserver.config.SettingsInfo;
import org.geoserver.gwc.GWC;
import org.geoserver.mapml.tcrs.GridSetToTCRSProvider;
import org.geoserver.mapml.tcrs.TiledCRSConstants;
import org.geoserver.web.util.MetadataMapModel;

public class MapMLTCRSSettingsPanel extends Panel {
    public MapMLTCRSSettingsPanel(String id, IModel<SettingsInfo> settingsInfoIModel) {
        super(id, settingsInfoIModel);

        final PropertyModel<MetadataMap> metadata =
                new PropertyModel<>(settingsInfoIModel, "metadata");

        MetadataMapModel metadataModel =
                new MetadataMapModel<>(metadata, TiledCRSConstants.TCRS_METADATA_KEY, List.class);

        List<String> names = GridSetToTCRSProvider.filterOut(GWC.get().getGridSetBroker().getGridSetNames());
        IModel<List<String>> availableGridSetsModel =
                new AbstractReadOnlyModel<List<String>>() {
                    @Override
                    public List<String> getObject() {
                        return names; // Return the list of gridsets
                    }
                };
        @SuppressWarnings("unchecked")
        Palette tcrsSelector =
                new Palette<String>(
                        "tcrspalette",
                        metadataModel,
                        availableGridSetsModel,
                        new MapMLTCRSSettingsPanel.TCSRenderer(),
                        7,
                        false) {

                    /** Override otherwise the header is not i18n'ized */
                    @Override
                    public Component newSelectedHeader(final String componentId) {
                        return new Label(
                                componentId, new ResourceModel("MapMLTCRSPanel.selectedHeader"));
                    }

                    /** Override otherwise the header is not i18n'ized */
                    @Override
                    public Component newAvailableHeader(final String componentId) {
                        return new Label(
                                componentId, new ResourceModel("MapMLTCRSPanel.availableHeader"));
                    }
                };
        tcrsSelector.add(new DefaultTheme());
        add(tcrsSelector);
    }

    static class TCSRenderer extends ChoiceRenderer<String> {
        /*@Override
        public Object getDisplayValue(String object) {
            if (object.equalsIgnoreCase(STATS)) {
                return new ParamResourceModel("JAIEXTPanel." + STATS, null, "").getString();
            } else if (object.equalsIgnoreCase(OPERATION_CONST)) {
                return new ParamResourceModel("JAIEXTPanel." + OPERATION_CONST, null, "")
                        .getString();
            } else if (object.equalsIgnoreCase(ALGEBRIC)) {
                return new ParamResourceModel("JAIEXTPanel." + ALGEBRIC, null, "").getString();
            } else {
                return object;
            }
        }*/

        @Override
        public String getIdValue(String object, int index) {
            return object;
        }
    }
}

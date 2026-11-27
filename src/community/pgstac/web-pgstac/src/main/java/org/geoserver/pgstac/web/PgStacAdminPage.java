/* (c) 2026 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.pgstac.web;

import java.io.IOException;
import java.io.Serializable;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.markup.html.form.CheckBox;
import org.apache.wicket.markup.html.form.DropDownChoice;
import org.apache.wicket.markup.html.form.EnumChoiceRenderer;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.markup.html.panel.EmptyPanel;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.request.mapper.parameter.PageParameters;
import org.apache.wicket.validation.validator.RangeValidator;
import org.geoserver.catalog.DataStoreInfo;
import org.geoserver.catalog.impl.DataStoreInfoImpl;
import org.geoserver.pgstac.DefaultPgStacFacade;
import org.geoserver.pgstac.PgStacServiceInfo;
import org.geoserver.pgstac.PgStacServiceInfo.ServiceLevel;
import org.geoserver.web.GeoServerApplication;
import org.geoserver.web.GeoserverAjaxSubmitLink;
import org.geoserver.web.data.store.DefaultDataStoreEditPanel;
import org.geoserver.web.data.store.ParamInfo;
import org.geoserver.web.services.AdminPagePanel;
import org.geoserver.web.services.BaseServiceAdminPage;
import org.geoserver.web.wicket.ParamResourceModel;

/** Settings page of the PgSTAC service: the database connection, paging and whether transactions are allowed. */
public class PgStacAdminPage extends BaseServiceAdminPage<PgStacServiceInfo> {

    /** The connection being edited, as a store only the PostGIS form sees; it never enters the catalog. */
    private DataStoreInfo connection;

    public PgStacAdminPage() {
        super();
    }

    public PgStacAdminPage(PageParameters pageParams) {
        super(pageParams);
    }

    public PgStacAdminPage(PgStacServiceInfo service) {
        super(service);
    }

    @Override
    protected Class<PgStacServiceInfo> getServiceClass() {
        return PgStacServiceInfo.class;
    }

    @Override
    protected String getServiceName() {
        return "PgSTAC";
    }

    @Override
    protected String getServiceType() {
        return "PgSTAC";
    }

    @Override
    protected AdminPagePanel buildPanel(String id, IModel<PgStacServiceInfo> info, Form form) {
        // called from the super constructor, before this class runs its field initializers
        connection = connectionStore(info.getObject());
        return new PgStacAdminPanel(id, info);
    }

    private DataStoreInfo connectionStore(PgStacServiceInfo info) {
        DataStoreInfoImpl store = (DataStoreInfoImpl) getCatalog().getFactory().createDataStore();
        store.setType("PostGIS");
        store.getConnectionParameters().putAll(info.getConnectionParameters());
        store.getConnectionParameters().putIfAbsent("dbtype", "postgis");
        // a store with an id is not new, so the form keeps the values instead of resetting them to the defaults
        store.setId("pgstac");
        return store;
    }

    @Override
    protected void handleSubmit(PgStacServiceInfo info) {
        info.setConnectionParameters(new LinkedHashMap<>(connection.getConnectionParameters()));
        super.handleSubmit(info);
    }

    private class PgStacAdminPanel extends AdminPagePanel {

        PgStacAdminPanel(String id, IModel<PgStacServiceInfo> info) {
            super(id, info);

            // nested, as the PostGIS form wants a store as its form model; submitted with the page form
            Form<DataStoreInfo> connectionForm = new Form<>("connectionForm", new Model<>(connection));
            connectionForm.add(new ConnectionPanel("connection", connectionForm));
            connectionForm.add(testLink(connectionForm));
            add(connectionForm);

            TextField<Integer> maxFeatures = new TextField<>("maxFeatures", Integer.class);
            maxFeatures.add(RangeValidator.minimum(1));
            maxFeatures.setRequired(true);
            add(maxFeatures);
            TextField<Integer> defaultPageSize = new TextField<>("defaultPageSize", Integer.class);
            defaultPageSize.add(RangeValidator.minimum(1));
            defaultPageSize.setRequired(true);
            add(defaultPageSize);
            add(new CheckBox("skipNumberMatched"));
            add(new CheckBox("useApiHydrate"));
            add(new DropDownChoice<>(
                    "serviceLevel", Arrays.asList(ServiceLevel.values()), new EnumChoiceRenderer<>(this)));
        }

        private GeoserverAjaxSubmitLink testLink(Form<DataStoreInfo> connectionForm) {
            return new GeoserverAjaxSubmitLink("test", connectionForm, PgStacAdminPage.this) {
                @Override
                protected void onSubmitInternal(AjaxRequestTarget target) {
                    DefaultPgStacFacade facade = GeoServerApplication.get().getBeanOfType(DefaultPgStacFacade.class);
                    PgStacAdminPage page = PgStacAdminPage.this;
                    try {
                        String version = facade.testConnection(connection.getConnectionParameters());
                        info(new ParamResourceModel("connectionOk", page, version).getString());
                    } catch (IOException e) {
                        error(new ParamResourceModel("connectionFailed", page, e.getMessage()).getString());
                    }
                }
            };
        }
    }

    /** The PostGIS store form without the namespace, which only matters to catalog stores. */
    private static class ConnectionPanel extends DefaultDataStoreEditPanel {

        ConnectionPanel(String id, Form<DataStoreInfo> form) {
            super(id, form);
        }

        @Override
        protected Panel getInputComponent(
                String componentId, IModel<Map<String, Serializable>> paramsModel, ParamInfo paramMetadata) {
            // not built at all: its model fails on the missing namespace
            if ("namespace".equals(paramMetadata.getName())) return new EmptyPanel(componentId);
            return super.getInputComponent(componentId, paramsModel, paramMetadata);
        }
    }
}

/* (c) 2026 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.pgstac.web;

import static org.geoserver.pgstac.PgStacFilters.toCQL2Json;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import org.apache.wicket.Component;
import org.apache.wicket.Session;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.markup.html.AjaxLink;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.Button;
import org.apache.wicket.markup.html.form.DropDownChoice;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.TextArea;
import org.apache.wicket.markup.html.panel.FeedbackPanel;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.markup.repeater.DefaultItemReuseStrategy;
import org.apache.wicket.model.CompoundPropertyModel;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.LoadableDetachableModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.model.PropertyModel;
import org.apache.wicket.validation.IValidatable;
import org.apache.wicket.validation.ValidationError;
import org.danekja.java.util.function.serializable.SerializableConsumer;
import org.geoserver.config.GeoServer;
import org.geoserver.pgstac.AccessRule;
import org.geoserver.pgstac.DefaultPgStacFacade;
import org.geoserver.pgstac.PgStacException;
import org.geoserver.pgstac.PgStacServiceInfo;
import org.geoserver.security.web.AbstractSecurityPage;
import org.geoserver.security.web.role.RoleNamesModel;
import org.geoserver.security.web.role.RolePaletteFormComponent;
import org.geoserver.web.GeoServerApplication;
import org.geoserver.web.wicket.GeoServerDataProvider;
import org.geoserver.web.wicket.GeoServerDialog;
import org.geoserver.web.wicket.GeoServerTablePanel;
import org.geoserver.web.wicket.ImageAjaxLink;
import org.geoserver.web.wicket.ParamResourceModel;
import org.geotools.filter.text.cql2.CQLException;
import org.geotools.filter.text.ecql.ECQL;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/** Edits the PgSTAC collection and item access rules; changes reach the service only on save or apply. */
public class PgStacSecurityPage extends AbstractSecurityPage {

    private final List<AccessRule> collectionRules;
    private final List<AccessRule> itemRules;

    public PgStacSecurityPage() {
        PgStacServiceInfo service = getGeoServer().getService(PgStacServiceInfo.class);
        collectionRules = copy(service.getCollectionAccessRules());
        itemRules = copy(service.getItemAccessRules());

        Form<Void> form = new Form<>("form");
        add(form);
        form.add(rules("collectionRules", collectionRules, false));
        form.add(rules("itemRules", itemRules, true));
        form.add(new Button("submit") {
            @Override
            public void onSubmit() {
                save();
                doReturn();
            }
        });
        form.add(new Button("apply") {
            @Override
            public void onSubmit() {
                save();
            }
        });
        Button cancel = new Button("cancel") {
            @Override
            public void onSubmit() {
                doReturn();
            }
        };
        form.add(cancel.setDefaultFormProcessing(false));
    }

    /** Deep copy, so rules edited on the page and the saved configuration never share state. */
    private static List<AccessRule> copy(List<AccessRule> rules) {
        List<AccessRule> copies = new ArrayList<>();
        for (AccessRule rule : rules) {
            AccessRule copy = new AccessRule();
            copy.setCollection(rule.getCollection());
            copy.setCqlFilter(rule.getCqlFilter());
            copy.setRoles(new LinkedHashSet<>(rule.getRoles()));
            copies.add(copy);
        }
        return copies;
    }

    private WebMarkupContainer rules(String id, List<AccessRule> rules, boolean item) {
        WebMarkupContainer container = new WebMarkupContainer(id);
        container.setOutputMarkupId(true);
        container.add(new AjaxLink<Void>("add") {
            @Override
            public void onClick(AjaxRequestTarget target) {
                showEditor(target, container, rules, new AccessRule(), item, true);
            }
        });
        container.add(new RuleTable("table", rules, container, item));
        return container;
    }

    private void showEditor(
            AjaxRequestTarget target,
            Component container,
            List<AccessRule> rules,
            AccessRule rule,
            boolean item,
            boolean add) {
        String titleKey = (add ? "add" : "edit") + (item ? "Item" : "Collection") + "Rule";
        dialog.setTitle(new ParamResourceModel(titleKey, this));
        dialog.setInitialHeight(500);
        dialog.showOkCancel(target, new GeoServerDialog.DialogDelegate() {
            @Override
            protected Component getContents(String id) {
                return new AccessRulePanel(id, new Model<>(rule), item);
            }

            @Override
            protected boolean onSubmit(AjaxRequestTarget target, Component contents) {
                if (add) rules.add(rule);
                target.add(container);
                return true;
            }
        });
    }

    private void save() {
        GeoServer gs = getGeoServer();
        PgStacServiceInfo service = gs.getService(PgStacServiceInfo.class);
        service.setCollectionAccessRules(copy(collectionRules));
        service.setItemAccessRules(copy(itemRules));
        gs.save(service);
    }

    private class RuleTable extends GeoServerTablePanel<AccessRule> {

        private final List<AccessRule> rules;
        private final Component container;
        private final boolean item;

        RuleTable(String id, List<AccessRule> rules, Component container, boolean item) {
            super(id, new RuleProvider(rules, item));
            this.rules = rules;
            this.container = container;
            this.item = item;
            setPageable(false);
            setFilterable(false);
            setSortable(false);
            setItemReuseStrategy(new DefaultItemReuseStrategy());
        }

        @Override
        protected Component getComponentForProperty(
                String id, IModel<AccessRule> itemModel, GeoServerDataProvider.Property<AccessRule> property) {
            AccessRule rule = itemModel.getObject();
            return switch (property.getName()) {
                case "roles" -> new Label(id, String.join(", ", rule.getRoles()));
                case "edit" ->
                    iconLink(id, "gs-icon-pencil", t -> showEditor(t, container, rules, rule, item, false));
                case "remove" -> iconLink(id, "gs-icon-delete", t -> {
                    rules.remove(rule);
                    t.add(container);
                });
                default -> null;
            };
        }

        private Component iconLink(String id, String icon, SerializableConsumer<AjaxRequestTarget> action) {
            return new ImageAjaxLink<Void>(id, icon) {
                @Override
                protected void onClick(AjaxRequestTarget target) {
                    action.accept(target);
                }
            };
        }
    }

    /** Rules in their configured order, which does not matter: each rule a user's roles miss hides what it matches. */
    private static class RuleProvider extends GeoServerDataProvider<AccessRule> {

        private final List<AccessRule> rules;
        private final List<Property<AccessRule>> properties = new ArrayList<>();

        RuleProvider(List<AccessRule> rules, boolean item) {
            this.rules = rules;
            if (item) properties.add(new BeanProperty<>("collection"));
            properties.add(new BeanProperty<>("cqlFilter"));
            properties.add(new PropertyPlaceholder<>("roles"));
            properties.add(new PropertyPlaceholder<>("edit"));
            properties.add(new PropertyPlaceholder<>("remove"));
        }

        @Override
        protected List<Property<AccessRule>> getProperties() {
            return properties;
        }

        @Override
        protected List<AccessRule> getItems() {
            return rules;
        }
    }

    /** Dialog content editing one rule; the collection shows for item rules only. */
    static class AccessRulePanel extends Panel {

        AccessRulePanel(String id, IModel<AccessRule> rule, boolean item) {
            super(id, rule);
            Form<AccessRule> form = new Form<>("form", new CompoundPropertyModel<>(rule));
            add(form);

            WebMarkupContainer collection = new WebMarkupContainer("collectionContainer");
            collection.setVisible(item);
            collection.add(new DropDownChoice<>("collection", new CollectionIds()).setRequired(true));
            form.add(collection);

            TextArea<String> filter = new TextArea<>("cqlFilter");
            filter.setRequired(true);
            filter.add(AccessRulePanel::validateFilter);
            form.add(filter);

            RolePaletteFormComponent roles =
                    new RolePaletteFormComponent("roles", new RoleNamesModel(new PropertyModel<>(rule, "roles")));
            roles.hideAddRoleLink();
            roles.getPalette().setRequired(true);
            form.add(roles);

            form.add(new FeedbackPanel("feedback").setOutputMarkupId(true));
        }

        /** Accepts the filters the service can apply: ECQL that converts to the CQL2 PgSTAC runs. */
        private static void validateFilter(IValidatable<String> filter) {
            try {
                toCQL2Json(ECQL.toFilter(filter.getValue()));
            } catch (CQLException | PgStacException e) {
                filter.error(new ValidationError("Invalid filter " + filter.getValue() + ": " + e.getMessage()));
            }
        }
    }

    /** All the PgSTAC collection ids, sorted; empty, with an error message, when the database cannot be read. */
    private static class CollectionIds extends LoadableDetachableModel<List<String>> {

        @Override
        protected List<String> load() {
            // PgSTAC returns 10 collections unless told otherwise, and rejects the largest int as limit
            ObjectNode request = JsonNodeFactory.instance.objectNode().put("limit", Integer.MAX_VALUE - 1);
            request.putObject("fields").putArray("include").add("id");
            List<String> ids = new ArrayList<>();
            try {
                DefaultPgStacFacade facade = GeoServerApplication.get().getBeanOfType(DefaultPgStacFacade.class);
                for (JsonNode collection : facade.collectionSearch(request).path("collections")) {
                    ids.add(collection.path("id").asString());
                }
            } catch (IOException e) {
                // the page stays usable to fix the rules while the database is down
                Session.get().error("Could not list the PgSTAC collections: " + e.getMessage());
            }
            Collections.sort(ids);
            return ids;
        }
    }
}

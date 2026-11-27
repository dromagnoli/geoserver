/* (c) 2026 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.pgstac.web;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.Assert.assertEquals;

import java.util.List;
import java.util.Set;
import org.apache.wicket.feedback.FeedbackMessage;
import org.apache.wicket.util.tester.FormTester;
import org.geoserver.config.GeoServer;
import org.geoserver.data.test.SystemTestData;
import org.geoserver.pgstac.AccessRule;
import org.geoserver.pgstac.DefaultPgStacFacade;
import org.geoserver.pgstac.PgStacServiceInfo;
import org.geoserver.pgstac.PgStacTestResource;
import org.geoserver.security.GeoServerRoleService;
import org.geoserver.security.GeoServerRoleStore;
import org.geoserver.security.impl.GeoServerRole;
import org.geoserver.web.GeoServerWicketTestSupport;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

public class PgStacSecurityPageTest extends GeoServerWicketTestSupport {

    @ClassRule
    public static final PgStacTestResource PGSTAC = new PgStacTestResource();

    private static final String COLLECTION =
            """
            {"type": "Collection", "id": "%s", "stac_version": "1.0.0", "description": "test", "license": "proprietary",
             "links": [],
             "extent": {"spatial": {"bbox": [[-180, -90, 180, 90]]},
                        "temporal": {"interval": [["2024-01-01T00:00:00Z", null]]}}}
            """;

    private static final String DIALOG_FORM = "dialog:dialog:modal:overlay:dialog:content:content:form";

    private static final String RULE_FORM = DIALOG_FORM + ":userPanel:form";

    private static final String ROLE = "ROLE_S2";

    @Override
    protected void onSetUp(SystemTestData testData) throws Exception {
        GeoServer gs = getGeoServer();
        PgStacServiceInfo info = gs.getService(PgStacServiceInfo.class);
        info.setConnectionParameters(PGSTAC.getConnectionParameters());
        gs.save(info);

        // created in reverse order, the dropdown sorts them
        DefaultPgStacFacade facade = applicationContext.getBean(DefaultPgStacFacade.class);
        JsonMapper mapper = new JsonMapper();
        for (String id : List.of("beta", "alpha")) {
            facade.createCollection((ObjectNode) mapper.readTree(COLLECTION.formatted(id)));
        }

        GeoServerRoleService roleService = getSecurityManager().getActiveRoleService();
        GeoServerRoleStore roleStore = roleService.createStore();
        roleStore.addRole(new GeoServerRole(ROLE));
        roleStore.store();
    }

    @Before
    public void reset() {
        GeoServer gs = getGeoServer();
        PgStacServiceInfo info = gs.getService(PgStacServiceInfo.class);
        info.setCollectionAccessRules(List.of(rule(null, "\"id\" = 'alpha'")));
        info.setItemAccessRules(List.of());
        gs.save(info);
        login();
    }

    @Test
    public void testAddRules() throws Exception {
        tester.startPage(PgStacSecurityPage.class);

        tester.clickLink("form:collectionRules:add", true);
        fillRule("\"id\" = 'beta'");
        tester.clickLink(DIALOG_FORM + ":submit", true);
        tester.assertNoErrorMessage();

        tester.clickLink("form:itemRules:add", true);
        FormTester form = fillRule("\"eo:cloud_cover\" < 10");
        // index 1 of the sorted collections
        form.select("collectionContainer:collection", 1);
        tester.clickLink(DIALOG_FORM + ":submit", true);
        tester.assertNoErrorMessage();

        tester.newFormTester("form").submit("submit");
        tester.assertNoErrorMessage();
        PgStacServiceInfo info = getGeoServer().getService(PgStacServiceInfo.class);
        assertEquals(
                List.of(rule(null, "\"id\" = 'alpha'"), rule(null, "\"id\" = 'beta'")),
                info.getCollectionAccessRules());
        assertEquals(List.of(rule("beta", "\"eo:cloud_cover\" < 10")), info.getItemAccessRules());
    }

    @Test
    public void testInvalidFilter() throws Exception {
        tester.startPage(PgStacSecurityPage.class);
        tester.clickLink("form:collectionRules:add", true);
        fillRule("\"id\" = = 'beta'");
        tester.clickLink(DIALOG_FORM + ":submit", true);

        List<String> errors = tester.getMessages(FeedbackMessage.ERROR).stream()
                .map(Object::toString)
                .toList();
        assertThat(errors, contains(startsWith("Invalid filter \"id\" = = 'beta': ")));
    }

    @Test
    public void testRemoveAndCancel() throws Exception {
        String remove = "form:collectionRules:table:listContainer:items:1:itemProperties:3:component:link";

        // a removal left unsaved does not reach the configuration
        tester.startPage(PgStacSecurityPage.class);
        tester.clickLink(remove, true);
        tester.newFormTester("form").submit("cancel");
        PgStacServiceInfo info = getGeoServer().getService(PgStacServiceInfo.class);
        assertEquals(1, info.getCollectionAccessRules().size());

        tester.startPage(PgStacSecurityPage.class);
        tester.clickLink(remove, true);
        tester.newFormTester("form").submit("apply");
        info = getGeoServer().getService(PgStacServiceInfo.class);
        assertThat(info.getCollectionAccessRules(), empty());
    }

    private FormTester fillRule(String filter) {
        FormTester form = tester.newFormTester(RULE_FORM);
        form.setValue("cqlFilter", filter);
        form.setValue("roles:palette:recorder", ROLE);
        return form;
    }

    private static AccessRule rule(String collection, String filter) {
        AccessRule rule = new AccessRule();
        rule.setCollection(collection);
        rule.setCqlFilter(filter);
        rule.setRoles(Set.of(ROLE));
        return rule;
    }
}

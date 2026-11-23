/* (c) 2026 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.pgstac;

import static org.geoserver.pgstac.PgStacServiceInfo.ServiceLevel.DISABLED;
import static org.geoserver.pgstac.PgStacServiceInfo.ServiceLevel.TRANSACTIONAL;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

import java.util.ArrayList;
import java.util.List;
import org.geoserver.catalog.Catalog;
import org.geoserver.catalog.WorkspaceInfo;
import org.geoserver.config.GeoServer;
import org.geoserver.data.test.SystemTestData;
import org.geoserver.ows.LocalWorkspace;
import org.geoserver.pgstac.PgStacServiceInfo.ServiceLevel;
import org.geoserver.test.GeoServerSystemTestSupport;
import org.junit.After;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.function.ThrowingRunnable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/** Runs the access rules, workspace filtering and write checks against a real PgSTAC 0.10.1 database. */
public class SecurePgStacFacadeIntegrationTest extends GeoServerSystemTestSupport {

    @ClassRule
    public static final PgStacTestResource PGSTAC = new PgStacTestResource();

    private static final JsonMapper MAPPER = new JsonMapper();

    private static final String COLLECTION =
            """
            {"type": "Collection", "id": "%s", "stac_version": "1.0.0", "description": "test", "license": "proprietary",
             "links": [],
             "extent": {"spatial": {"bbox": [[-180, -90, 180, 90]]},
                        "temporal": {"interval": [["2024-01-01T00:00:00Z", null]]}}}
            """;

    private static final String ITEM =
            """
            {"type": "Feature", "stac_version": "1.0.0", "id": "%s", "collection": "%s", "links": [], "assets": {},
             "geometry": {"type": "Point", "coordinates": [10, 20]}, "bbox": [10, 20, 10, 20],
             "properties": {"datetime": "2024-01-15T00:00:00Z", "eo:cloud_cover": %d}}
            """;

    @Override
    protected void setUpTestData(SystemTestData testData) throws Exception {
        // no catalog layers needed, only the security setup
        testData.setUpSecurity();
    }

    @Override
    protected void onSetUp(SystemTestData testData) throws Exception {
        GeoServer gs = getGeoServer();
        PgStacServiceInfo info = gs.getService(PgStacServiceInfo.class);
        info.setConnectionParameters(PGSTAC.getConnectionParameters());
        // id is an ECQL keyword (feature id filters), so the property must be quoted
        info.getCollectionAccessRules().add(rule(null, "\"id\" = 'restricted'", "ROLE_S2"));
        info.getItemAccessRules().add(rule("open", "\"eo:cloud_cover\" >= 20", "ROLE_FULL"));
        gs.save(info);
        addWorkspace("cite");
        addWorkspace("sf");

        PgStacFacade facade = applicationContext.getBean(DefaultPgStacFacade.class);
        facade.createCollection(collection("open", null));
        facade.createCollection(collection("restricted", null));
        facade.createCollection(collection("cite-only", "cite"));
        facade.createItem(item("open", "clear", 5));
        facade.createItem(item("open", "cloudy", 50));
        facade.createItem(item("restricted", "r1", 5));
        facade.createItem(item("cite-only", "c1", 5));
    }

    @After
    public void cleanUp() {
        logout();
        LocalWorkspace.remove();
        setServiceLevel(DISABLED);
    }

    @Test
    public void testAnonymousSeesUnrestricted() throws Exception {
        assertThat(searchIds(), containsInAnyOrder("clear", "c1"));
        assertThat(collectionIds(), containsInAnyOrder("open", "cite-only"));
    }

    @Test
    public void testRolesGrantAccess() throws Exception {
        login("s2", "pwd", "ROLE_S2");
        assertThat(searchIds(), containsInAnyOrder("clear", "r1", "c1"));
        login("full", "pwd", "ROLE_FULL");
        assertThat(searchIds(), containsInAnyOrder("clear", "cloudy", "c1"));
    }

    @Test
    public void testAdminSeesEverything() throws Exception {
        loginAsAdmin();
        assertThat(searchIds(), containsInAnyOrder("clear", "cloudy", "r1", "c1"));
    }

    @Test
    public void testRequestedCollectionsRestricted() throws Exception {
        assertThat(searchIds("restricted"), empty());
        assertThat(searchIds("open", "restricted"), contains("clear"));
    }

    @Test
    public void testHiddenSingleResources() throws Exception {
        PgStacFacade facade = facade();
        assertNull(facade.getCollection("restricted"));
        assertNull(facade.getItem("restricted", "r1"));
        assertNull(facade.getItem("open", "cloudy"));
        assertNull(facade.getQueryables("restricted"));
        assertEquals("open", facade.getCollection("open").path("id").asString());
        assertEquals("clear", facade.getItem("open", "clear").path("id").asString());
    }

    @Test
    public void testWorkspaceFilter() throws Exception {
        // looked up first: with a local workspace set, the catalog hides the other ones
        WorkspaceInfo cite = getCatalog().getWorkspaceByName("cite");
        WorkspaceInfo sf = getCatalog().getWorkspaceByName("sf");

        LocalWorkspace.set(cite);
        assertThat(collectionIds(), containsInAnyOrder("open", "cite-only"));

        LocalWorkspace.set(sf);
        assertThat(collectionIds(), contains("open"));
        assertThat(searchIds(), contains("clear"));
        assertNull(facade().getCollection("cite-only"));

        // workspaces scope administrators too
        loginAsAdmin();
        assertThat(collectionIds(), containsInAnyOrder("open", "restricted"));
    }

    @Test
    public void testWritesNeedTransactionalAdmin() throws Exception {
        ObjectNode item = item("open", "new", 5);
        assertForbidden(() -> facade().createItem(item));

        loginAsAdmin();
        assertForbidden(() -> facade().createItem(item));
        setServiceLevel(TRANSACTIONAL);
        facade().createItem(item);
        facade().deleteItem("open", "new");

        logout();
        assertForbidden(() -> facade().deleteItem("open", "clear"));
    }

    private PgStacFacade facade() {
        return applicationContext.getBean(PgStacFacade.class);
    }

    private List<String> searchIds(String... collections) throws Exception {
        ObjectNode request = MAPPER.createObjectNode();
        if (collections.length > 0) {
            ArrayNode array = request.putArray("collections");
            for (String collection : collections) array.add(collection);
        }
        return ids(facade().search(request).path("features"));
    }

    private List<String> collectionIds() throws Exception {
        return ids(facade().collectionSearch(MAPPER.createObjectNode()).path("collections"));
    }

    private static List<String> ids(JsonNode objects) {
        List<String> ids = new ArrayList<>();
        for (JsonNode object : objects) {
            ids.add(object.path("id").asString());
        }
        return ids;
    }

    private static void assertForbidden(ThrowingRunnable write) {
        assertEquals(403, assertThrows(PgStacException.class, write).getHttpStatus());
    }

    private void setServiceLevel(ServiceLevel level) {
        GeoServer gs = getGeoServer();
        PgStacServiceInfo info = gs.getService(PgStacServiceInfo.class);
        info.setServiceLevel(level);
        gs.save(info);
    }

    private void addWorkspace(String name) {
        Catalog catalog = getCatalog();
        if (catalog.getWorkspaceByName(name) != null) return;
        WorkspaceInfo workspace = catalog.getFactory().createWorkspace();
        workspace.setName(name);
        catalog.add(workspace);
    }

    private static AccessRule rule(String collection, String cqlFilter, String role) {
        AccessRule rule = new AccessRule();
        rule.setCollection(collection);
        rule.setCqlFilter(cqlFilter);
        rule.getRoles().add(role);
        return rule;
    }

    private static ObjectNode collection(String id, String workspace) {
        ObjectNode collection = (ObjectNode) MAPPER.readTree(COLLECTION.formatted(id));
        if (workspace != null)
            collection.putObject(GeoServerMetadata.KEY).putArray("workspaces").add(workspace);
        return collection;
    }

    private static ObjectNode item(String collection, String id, int cloudCover) {
        return (ObjectNode) MAPPER.readTree(ITEM.formatted(id, collection, cloudCover));
    }
}

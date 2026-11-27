/* (c) 2026 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.pgstac.stac;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import org.geoserver.pgstac.PgStacFacade;
import org.geoserver.pgstac.PgStacServiceInfo;
import org.geoserver.pgstac.PgStacServiceInfo.ServiceLevel;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletResponse;

/** Checks the STAC transaction endpoints: status codes, path and body rules, and the service level and admin gate. */
public class TransactionTest extends PgStacApiTestSupport {

    private static final String ITEM =
            """
            {"type": "Feature", "stac_version": "1.0.0", "id": "%s", "links": [], "assets": {},
             "geometry": {"type": "Point", "coordinates": [10, 20]}, "bbox": [10, 20, 10, 20],
             "properties": {"datetime": "2024-01-15T00:00:00Z", "eo:cloud_cover": %d}}
            """;

    private static final String GEOJSON = "application/geo+json";

    private static final String JSON = "application/json";

    @Override
    protected void configure(PgStacServiceInfo info) {
        info.setServiceLevel(ServiceLevel.TRANSACTIONAL);
    }

    @Override
    protected void loadData(PgStacFacade facade) throws Exception {
        facade.createCollection(json(COLLECTION, "open"));
        facade.createItem(json(ITEM, "existing", 5).put("collection", "open"));
    }

    @Before
    public void admin() {
        loginAsAdmin();
    }

    @After
    public void cleanUp() {
        logout();
    }

    @Test
    public void testItemLifecycle() throws Exception {
        // the collection comes from the path
        MockHttpServletResponse created = postJson("collections/open/items", ITEM.formatted("new", 10), GEOJSON);
        assertEquals(201, created.getStatus());
        assertEquals(BASE + "collections/open/items/new", created.getHeader("Location"));
        DocumentContext item = JsonPath.parse(created.getContentAsString());
        assertEquals("open", item.read("collection"));
        assertThat(item.read("links[?(@.rel == 'self')].href"), contains(BASE + "collections/open/items/new"));

        MockHttpServletResponse replaced = putJson("collections/open/items/new", ITEM.formatted("new", 30), GEOJSON);
        assertEquals(200, replaced.getStatus());
        assertEquals(30, (int) JsonPath.read(replaced.getContentAsString(), "properties['eo:cloud_cover']"));
        assertEquals(30, (int)
                getAsJSONPath("ogc/stac/v1/collections/open/items/new", 200).read("properties['eo:cloud_cover']"));

        assertEquals(
                204,
                deleteAsServletResponse("ogc/stac/v1/collections/open/items/new")
                        .getStatus());
        getAsJSONPath("ogc/stac/v1/collections/open/items/new", 404);
        assertEquals(
                404,
                deleteAsServletResponse("ogc/stac/v1/collections/open/items/new")
                        .getStatus());
    }

    @Test
    public void testCollectionLifecycle() throws Exception {
        MockHttpServletResponse created = postJson("collections", COLLECTION.formatted("fresh"), JSON);
        assertEquals(201, created.getStatus());
        assertEquals(BASE + "collections/fresh", created.getHeader("Location"));
        assertEquals("fresh", JsonPath.read(created.getContentAsString(), "id"));
        assertEquals(
                201,
                postJson("collections/fresh/items", ITEM.formatted("f1", 5), GEOJSON)
                        .getStatus());

        String titled = json(COLLECTION, "fresh").put("title", "Fresh").toString();
        MockHttpServletResponse replaced = putJson("collections/fresh", titled, JSON);
        assertEquals(200, replaced.getStatus());
        assertEquals("Fresh", JsonPath.read(replaced.getContentAsString(), "title"));

        // the items go with the collection
        assertEquals(
                204, deleteAsServletResponse("ogc/stac/v1/collections/fresh").getStatus());
        getAsJSONPath("ogc/stac/v1/collections/fresh", 404);
        assertThat(getAsJSONPath("ogc/stac/v1/search?ids=f1", 200).read("features"), empty());
    }

    @Test
    public void testConflict() throws Exception {
        // the object named, not the PostgreSQL constraint
        String existing = ITEM.formatted("existing", 5);
        String item = assertError(409, postJson("collections/open/items", existing, GEOJSON));
        assertEquals("Item existing in collection open already exists", item);
        String collection = assertError(409, postJson("collections", COLLECTION.formatted("open"), JSON));
        assertEquals("Collection open already exists", collection);
    }

    @Test
    public void testNotFound() throws Exception {
        assertError(404, postJson("collections/missing/items", ITEM.formatted("m1", 5), GEOJSON));
        String missing = ITEM.formatted("missing", 5);
        String replaced = assertError(404, putJson("collections/open/items/missing", missing, GEOJSON));
        // PostgreSQL says "query returned no rows" here
        assertEquals("Item missing in collection open not found", replaced);
        String deleted = assertError(404, deleteAsServletResponse("ogc/stac/v1/collections/open/items/missing"));
        assertEquals("Item missing in collection open not found", deleted);
        assertError(404, putJson("collections/missing", COLLECTION.formatted("missing"), JSON));
        assertError(404, deleteAsServletResponse("ogc/stac/v1/collections/missing"));
    }

    @Test
    public void testBodyMustMatchPath() throws Exception {
        // see the STAC transaction extension: 400 when id or collection differ from the path
        assertError(400, putJson("collections/open/items/existing", ITEM.formatted("other", 5), GEOJSON));
        String elsewhere = json(ITEM, "e1", 5).put("collection", "other").toString();
        assertError(400, postJson("collections/open/items", elsewhere, GEOJSON));
        assertError(400, putJson("collections/open", COLLECTION.formatted("other"), JSON));
        assertError(400, postJson("collections/open/items", "{\"type\": \"Feature\"}", GEOJSON));
        String itemCollection = "{\"type\": \"FeatureCollection\", \"features\": [" + ITEM.formatted("b1", 5) + "]}";
        assertError(400, postJson("collections/open/items", itemCollection, GEOJSON));
    }

    @Test
    public void testAdminOnly() throws Exception {
        logout();
        assertError(403, postJson("collections/open/items", ITEM.formatted("anonymous", 5), GEOJSON));
        login("user", "pwd", "ROLE_USER");
        assertError(403, putJson("collections/open/items/existing", ITEM.formatted("existing", 50), GEOJSON));
        assertError(403, deleteAsServletResponse("ogc/stac/v1/collections/open/items/existing"));
        assertEquals(5, (int) getAsJSONPath("ogc/stac/v1/collections/open/items/existing", 200)
                .read("properties['eo:cloud_cover']"));
    }

    @Test
    public void testServiceLevel() throws Exception {
        assertThat(conformance(), hasItems(PgStacService.TRANSACTION_CONFORMANCE.toArray(String[]::new)));
        PgStacServiceInfo info = getGeoServer().getService(PgStacServiceInfo.class);
        info.setServiceLevel(ServiceLevel.DISABLED);
        getGeoServer().save(info);
        try {
            assertError(403, postJson("collections/open/items", ITEM.formatted("disabled", 5), GEOJSON));
            assertEquals(PgStacService.CONFORMANCE, conformance());
            assertThat(
                    getAsJSONPath("ogc/stac/v1", 200).read("conformsTo"),
                    not(hasItem(PgStacService.TRANSACTION_CONFORMANCE.get(0))));
        } finally {
            info.setServiceLevel(ServiceLevel.TRANSACTIONAL);
            getGeoServer().save(info);
        }
    }

    private List<String> conformance() throws Exception {
        return getAsJSONPath("ogc/stac/v1/conformance", 200).read("conformsTo");
    }

    private MockHttpServletResponse postJson(String path, String body, String contentType) throws Exception {
        return postAsServletResponse("ogc/stac/v1/" + path, body, contentType);
    }

    private MockHttpServletResponse putJson(String path, String body, String contentType) throws Exception {
        return putAsServletResponse("ogc/stac/v1/" + path, body, contentType);
    }

    /** Checks the status and the STAC error body, returning its description. */
    private static String assertError(int status, MockHttpServletResponse response) throws Exception {
        String body = response.getContentAsString();
        assertEquals(body, status, response.getStatus());
        assertThat(JsonPath.<String>read(body, "code"), not(emptyOrNullString()));
        return JsonPath.read(body, "description");
    }
}

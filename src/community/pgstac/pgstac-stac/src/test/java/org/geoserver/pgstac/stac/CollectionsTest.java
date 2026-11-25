/* (c) 2026 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.pgstac.stac;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;

import com.jayway.jsonpath.DocumentContext;
import java.util.List;
import org.geoserver.config.GeoServer;
import org.geoserver.data.test.SystemTestData;
import org.geoserver.ogcapi.OGCApiTestSupport;
import org.geoserver.pgstac.DefaultPgStacFacade;
import org.geoserver.pgstac.PgStacServiceInfo;
import org.geoserver.pgstac.PgStacTestResource;
import org.junit.ClassRule;
import org.junit.Test;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/** Checks the collections and queryables endpoints against a real PgSTAC database. */
public class CollectionsTest extends OGCApiTestSupport {

    @ClassRule
    public static final PgStacTestResource PGSTAC = new PgStacTestResource();

    private static final String BASE = "http://localhost:8080/geoserver/ogc/stac/v1/";

    private static final String COLLECTION =
            """
            {"type": "Collection", "id": "%s", "stac_version": "1.0.0", "description": "test", "license": "proprietary",
             "links": [{"rel": "self", "href": "/stored/self.json"}, {"rel": "license", "href": "https://example.com/license"}],
             "extent": {"spatial": {"bbox": [[-180, -90, 180, 90]]},
                        "temporal": {"interval": [["2024-01-01T00:00:00Z", null]]}},
             "_geoserver": {"workspaces": ["sf"]}}
            """;

    @Override
    protected void setUpTestData(SystemTestData testData) throws Exception {
        // the collections come from PgSTAC, no catalog layers needed
        testData.setUpSecurity();
    }

    @Override
    protected void onSetUp(SystemTestData testData) throws Exception {
        super.onSetUp(testData);
        GeoServer gs = getGeoServer();
        PgStacServiceInfo info = gs.getService(PgStacServiceInfo.class);
        info.setConnectionParameters(PGSTAC.getConnectionParameters());
        gs.save(info);
        DefaultPgStacFacade facade = applicationContext.getBean(DefaultPgStacFacade.class);
        JsonMapper mapper = new JsonMapper();
        for (String id : List.of("a", "b", "c")) {
            facade.createCollection((ObjectNode) mapper.readTree(COLLECTION.formatted(id)));
        }
    }

    @Test
    public void testCollectionsPaging() throws Exception {
        DocumentContext first = getAsJSONPath("ogc/stac/v1/collections?limit=2", 200);
        assertThat(first.read("collections[*].id"), contains("a", "b"));
        assertEquals(Integer.valueOf(3), first.read("numberMatched"));
        assertThat(first.read("links[?(@.rel == 'next')].href"), contains(BASE + "collections?limit=2&offset=2"));
        assertThat(first.read("links[?(@.rel == 'prev')]"), empty());

        DocumentContext last = getAsJSONPath("ogc/stac/v1/collections?limit=2&offset=2", 200);
        assertThat(last.read("collections[*].id"), contains("c"));
        assertThat(last.read("links[?(@.rel == 'prev')].href"), contains(BASE + "collections?limit=2&offset=0"));
        assertThat(last.read("links[?(@.rel == 'next')]"), empty());
    }

    @Test
    public void testCollection() throws Exception {
        DocumentContext json = getAsJSONPath("ogc/stac/v1/collections/a", 200);
        assertEquals("a", json.read("id"));
        // the stored self link is replaced, the other stored links are kept
        assertThat(json.read("links[?(@.rel == 'self')].href"), contains(BASE + "collections/a"));
        assertThat(json.read("links[*].rel"), containsInAnyOrder("license", "self", "root", "parent", "items"));
        assertThat(json.read("$.keys()"), not(hasItem("_geoserver")));
    }

    @Test
    public void testCollectionNotFound() throws Exception {
        DocumentContext json = getAsJSONPath("ogc/stac/v1/collections/missing", 404);
        assertEquals("NotFound", json.read("code"));
        assertEquals("Collection not found: missing", json.read("description"));
    }

    @Test
    public void testInvalidOffset() throws Exception {
        // raised as a PgStacException, which carries its own status
        DocumentContext json = getAsJSONPath("ogc/stac/v1/collections?offset=-1", 400);
        assertEquals("InvalidParameterValue", json.read("code"));
    }

    @Test
    public void testQueryables() throws Exception {
        DocumentContext global = getAsJSONPath("ogc/stac/v1/queryables", 200);
        assertEquals(BASE + "queryables", global.read("$id"));
        assertThat(global.read("properties.keys()"), hasItem("datetime"));

        DocumentContext collection = getAsJSONPath("ogc/stac/v1/collections/a/queryables", 200);
        assertEquals(BASE + "collections/a/queryables", collection.read("$id"));

        getAsJSONPath("ogc/stac/v1/collections/missing/queryables", 404);
    }
}

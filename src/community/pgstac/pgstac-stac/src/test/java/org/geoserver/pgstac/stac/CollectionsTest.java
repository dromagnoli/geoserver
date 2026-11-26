/* (c) 2026 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.pgstac.stac;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.Assert.assertEquals;

import com.jayway.jsonpath.DocumentContext;
import java.util.List;
import org.geoserver.config.GeoServerInfo;
import org.geoserver.pgstac.PgStacFacade;
import org.junit.Test;

/** Checks the collections and queryables endpoints against a real PgSTAC database. */
public class CollectionsTest extends PgStacApiTestSupport {

    private static final String LINKED_COLLECTION =
            """
            {"type": "Collection", "id": "%s", "stac_version": "1.0.0", "description": "test", "license": "proprietary",
             "links": [{"rel": "self", "href": "/stored/self.json"}, {"rel": "license", "href": "https://example.com/license"}],
             "extent": {"spatial": {"bbox": [[-180, -90, 180, 90]]},
                        "temporal": {"interval": [["2024-01-01T00:00:00Z", null]]}},
             "_geoserver": {"workspaces": ["sf"]}}
            """;

    @Override
    protected void loadData(PgStacFacade facade) throws Exception {
        for (String id : List.of("a", "b", "c")) facade.createCollection(json(LINKED_COLLECTION, id));
    }

    @Test
    public void testCollectionsPaging() throws Exception {
        // PgSTAC default order: datetime descending, then id in the same direction
        DocumentContext first = getAsJSONPath("ogc/stac/v1/collections?limit=2", 200);
        assertThat(first.read("collections[*].id"), contains("c", "b"));
        assertEquals(Integer.valueOf(3), first.read("numberMatched"));
        assertThat(first.read("links[?(@.rel == 'next')].href"), contains(BASE + "collections?limit=2&offset=2"));
        assertThat(first.read("links[?(@.rel == 'prev')]"), empty());

        DocumentContext last = getAsJSONPath("ogc/stac/v1/collections?limit=2&offset=2", 200);
        assertThat(last.read("collections[*].id"), contains("a"));
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
        assertEquals(BASE + "queryables", global.read("$['$id']"));
        assertThat(global.read("properties.keys()"), hasItem("datetime"));

        DocumentContext collection = getAsJSONPath("ogc/stac/v1/collections/a/queryables", 200);
        assertEquals(BASE + "collections/a/queryables", collection.read("$['$id']"));

        getAsJSONPath("ogc/stac/v1/collections/missing/queryables", 404);
    }

    @Test
    public void testProxyBaseUrl() throws Exception {
        GeoServerInfo global = getGeoServer().getGlobal();
        global.getSettings().setProxyBaseUrl("https://proxy.example.com/geoserver");
        getGeoServer().save(global);
        try {
            DocumentContext json = getAsJSONPath("ogc/stac/v1/collections?limit=1", 200);
            // no trailing slash: the root link is the landing page itself
            String base = "https://proxy.example.com/geoserver/ogc/stac/v1";
            List<String> pageLinks = json.read("links[*].href");
            assertThat(pageLinks, everyItem(startsWith(base)));
            List<String> selfLinks = json.read("collections[*].links[?(@.rel == 'self')].href");
            assertThat(selfLinks, everyItem(startsWith(base)));
        } finally {
            global.getSettings().setProxyBaseUrl(null);
            getGeoServer().save(global);
        }
    }
}

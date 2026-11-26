/* (c) 2026 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.pgstac.stac;

import static org.geoserver.pgstac.stac.SearchTest.ANTIMERIDIAN_ID;
import static org.geoserver.pgstac.stac.SearchTest.ANTIMERIDIAN_ITEM;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.junit.Assert.assertEquals;

import java.util.List;
import org.geoserver.pgstac.PgStacFacade;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Checks the HTML pages, and that the JSON embedded for the map is the document of the request, kept inert. */
public class HtmlTest extends PgStacApiTestSupport {

    private static final String AM_COLLECTION = "TSM.CAT.SRA.SC.S";

    private static final String HOSTILE = "</script><script>alert(1)</script>";

    private static final String ITEM =
            """
            {"type": "Feature", "stac_version": "1.0.0", "id": "plain", "collection": "%s", "links": [],
             "assets": {"thumbnail": {"href": "https://example.com/plain.png", "title": "Thumb"}},
             "geometry": {"type": "Point", "coordinates": [10, 20]}, "bbox": [10, 20, 10, 20],
             "properties": {"datetime": "2024-01-01T00:00:00Z", "note": "%s"}}
            """;

    @Override
    protected void loadData(PgStacFacade facade) throws Exception {
        facade.createCollection(json(COLLECTION, AM_COLLECTION));
        facade.createItem(json(ANTIMERIDIAN_ITEM, ANTIMERIDIAN_ID));
        facade.createItem(json(ITEM, AM_COLLECTION, HOSTILE));
    }

    @Test
    public void testLandingPage() throws Exception {
        Document doc = getAsJSoup("ogc/stac/v1?f=text/html");
        assertThat(doc.select("#links a").eachAttr("href"), hasItem(BASE + "collections"));
    }

    @Test
    public void testConformance() throws Exception {
        Document doc = getAsJSoup("ogc/stac/v1/conformance?f=text/html");
        assertEquals(PgStacService.CONFORMANCE, doc.select("#conformance a").eachText());
    }

    @Test
    public void testCollections() throws Exception {
        Document doc = getAsJSoup("ogc/stac/v1/collections?f=text/html");
        assertThat(doc.select("#collections h2 a").eachText(), contains(AM_COLLECTION));

        Document collection = getAsJSoup("ogc/stac/v1/collections/" + AM_COLLECTION + "?f=text/html");
        assertEquals(AM_COLLECTION, collection.getElementById("title").text());
    }

    @Test
    public void testItemsMap() throws Exception {
        Document doc = getAsJSoup("ogc/stac/v1/collections/" + AM_COLLECTION + "/items?f=text/html");
        assertThat(doc.select("#items a").eachText(), hasItem(ANTIMERIDIAN_ID));
        // the map gets the page as GeoJSON, the split footprint as one MultiPolygon
        JsonNode page = embeddedJSON(doc);
        List<String> types = page.path("features")
                .valueStream()
                .map(f -> f.path("geometry").path("type").asString())
                .toList();
        assertThat(types, hasItem("MultiPolygon"));
    }

    @Test
    public void testItemKeepsScriptInert() throws Exception {
        Document doc = getAsJSoup("ogc/stac/v1/collections/" + AM_COLLECTION + "/items/plain?f=text/html");
        // a stored "</script>" neither ends the JSON block nor adds a script
        assertEquals(HOSTILE, embeddedJSON(doc).path("properties").path("note").asString());
        // header, JSON block, OpenLayers and the map script
        assertEquals(4, doc.select("script").size());
        assertThat(doc.select("#properties").text(), containsString(HOSTILE));
        assertThat(doc.select("#assets a").eachAttr("href"), contains("https://example.com/plain.png"));
    }

    @Test
    public void testSchema() throws Exception {
        Document doc = getAsJSoup("ogc/stac/v1/collections/" + AM_COLLECTION + "/sortables?f=text/html");
        assertEquals("Sortables", doc.getElementById("title").text());
        assertThat(doc.select("#properties td:first-child").eachText(), hasItem("datetime"));
    }

    private static JsonNode embeddedJSON(Document doc) {
        Element data = doc.getElementById("footprints-data");
        return new JsonMapper().readTree(data.data());
    }
}

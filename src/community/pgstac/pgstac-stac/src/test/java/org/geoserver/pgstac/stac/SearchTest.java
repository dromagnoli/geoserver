/* (c) 2026 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.pgstac.stac;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;

import com.jayway.jsonpath.DocumentContext;
import java.net.URLEncoder;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.geoserver.pgstac.PgStacFacade;
import org.geoserver.pgstac.PgStacServiceInfo;
import org.junit.Test;

/**
 * Checks item search over GET and POST: each parameter, filters kept across pages, footprints split at the
 * antimeridian.
 */
public class SearchTest extends PgStacApiTestSupport {

    private static final String ITEM =
            """
            {"type": "Feature", "stac_version": "1.0.0", "id": "%s", "collection": "s", "links": [], "assets": {},
             "geometry": {"type": "Point", "coordinates": [10, 20]}, "bbox": [10, 20, 10, 20],
             "properties": {"datetime": "2024-01-0%dT00:00:00Z", "cloud": %d}}
            """;

    /** Selects the collection of the antimeridian item. */
    private static final String AM = "collections=TSM.CAT.SRA.SC.S&";

    static final String ANTIMERIDIAN_ID = "TSX_L0_SRA_SC_S_scan_003_20151020T203139_20151020T203153_NSG";

    /** The DLR item whose footprint is split at the antimeridian, trimmed to what the search needs. */
    static final String ANTIMERIDIAN_ITEM =
            """
            {"type": "Feature", "stac_version": "1.0.0", "id": "%s", "collection": "TSM.CAT.SRA.SC.S", "links": [], "assets": {},
             "geometry": {"type": "MultiPolygon", "coordinates": [
               [[[179.71184, 81.96563], [177.0627, 81.462746], [176.10414, 81.26094], [180, 80.793999],
                 [180, 81.655109], [179.71184, 81.96563]]],
               [[[-180, 80.793999], [-179.1202, 80.68855], [-178.11687, 80.878204], [-175.37206, 81.348335],
                 [-180, 81.655109], [-180, 80.793999]]]]},
             "bbox": [176.10414, 80.68855, -175.37206, 81.96563],
             "properties": {"datetime": "2015-10-20T20:31:46Z",
                            "start_datetime": "2015-10-20T20:31:39.313Z", "end_datetime": "2015-10-20T20:31:53.24Z"}}
            """;

    @Override
    protected void loadData(PgStacFacade facade) throws Exception {
        for (String collection : List.of("s", "TSM.CAT.SRA.SC.S")) {
            facade.createCollection(json(COLLECTION, collection));
        }
        facade.createItem(json(ITEM, "low1", 1, 5));
        facade.createItem(json(ITEM, "high", 2, 50));
        facade.createItem(json(ITEM, "low2", 3, 10));
        facade.createItem(json(ANTIMERIDIAN_ITEM, ANTIMERIDIAN_ID));
    }

    @Test
    public void testGetPagingKeepsFilter() throws Exception {
        DocumentContext first = getAsJSONPath("ogc/stac/v1/search?collections=s&filter=cloud%20%3C%2020&limit=1", 200);
        assertThat(first.read("features[*].id"), contains("low2"));
        List<String> next = first.read("links[?(@.rel == 'next')].href");

        // the second page is still filtered, so the cloudy item in the middle is skipped
        DocumentContext second = getAsJSONPath(next.get(0).substring(ROOT.length()), 200);
        assertThat(second.read("features[*].id"), contains("low1"));
        assertThat(second.read("links[?(@.rel == 'next')]"), empty());
    }

    @Test
    public void testTokenRepeatable() throws Exception {
        DocumentContext first = getAsJSONPath("ogc/stac/v1/search?collections=s&limit=1", 200);
        List<String> next = first.read("links[?(@.rel == 'next')].href");
        String path = next.get(0).substring(ROOT.length());
        List<String> page = getAsJSONPath(path, 200).read("features[*].id");
        assertThat(page, hasSize(1));
        assertEquals(page, getAsJSONPath(path, 200).read("features[*].id"));
    }

    @Test
    public void testPostPaging() throws Exception {
        String body =
                """
                {"collections": ["s"], "filter-lang": "cql2-text", "filter": "cloud < 20", "limit": 1}
                """;
        DocumentContext first = postAsJSONPath("ogc/stac/v1/search", body, 200);
        assertThat(first.read("features[*].id"), contains("low2"));
        List<Map<String, Object>> next = first.read("links[?(@.rel == 'next')]");
        assertEquals(BASE + "search", next.get(0).get("href"));
        assertEquals("POST", next.get(0).get("method"));
        assertEquals(Boolean.TRUE, next.get(0).get("merge"));

        // merge the token into the original body, as a client does
        List<String> tokens = first.read("links[?(@.rel == 'next')].body.token");
        String token = tokens.get(0);
        String merged = body.replace("\"limit\": 1", "\"limit\": 1, \"token\": \"" + token + "\"");
        assertThat(postAsJSONPath("ogc/stac/v1/search", merged, 200).read("features[*].id"), contains("low1"));
    }

    @Test
    public void testAntimeridianFootprint() throws Exception {
        for (String bbox : List.of("179,81,180,82", "-180,80.9,-179,81.2", "175,80,-175,82")) {
            assertEquals(bbox, List.of(ANTIMERIDIAN_ID), searchIds(AM + "bbox=" + bbox));
        }
        // a point on the far side of the globe, inside the footprint only when wrongly read as one polygon
        String farPoint = "%7B%22type%22%3A%22Point%22%2C%22coordinates%22%3A%5B0%2C81.5%5D%7D";
        assertThat(searchIds(AM + "intersects=" + farPoint), empty());
    }

    @Test
    public void testDatetime() throws Exception {
        assertThat(searchIds("collections=s&datetime=2024-01-02T00:00:00Z"), contains("high"));
        String interval = "2024-01-02T00:00:00Z/2024-01-03T00:00:00Z";
        assertThat(searchIds("collections=s&datetime=" + interval), contains("low2", "high"));
        assertThat(searchIds("collections=s&datetime=../2024-01-01T12:00:00Z"), contains("low1"));
    }

    @Test
    public void testIdsAndCollections() throws Exception {
        assertThat(searchIds("collections=s&ids=low1,high"), contains("high", "low1"));
        assertThat(
                searchIds("collections=s,TSM.CAT.SRA.SC.S"),
                containsInAnyOrder("low1", "high", "low2", ANTIMERIDIAN_ID));
        // an unknown collection is an empty result, as for any other selection
        assertThat(searchIds("collections=missing"), empty());
    }

    @Test
    public void testIntersects() throws Exception {
        String square = "{\"type\":\"Polygon\",\"coordinates\":[[[9,19],[11,19],[11,21],[9,21],[9,19]]]}";
        assertThat(searchIds("intersects=" + encode(square)), containsInAnyOrder("low1", "high", "low2"));
    }

    @Test
    public void testSortBy() throws Exception {
        assertThat(searchIds("collections=s&sortby=id"), contains("high", "low1", "low2"));
        assertThat(searchIds("collections=s&sortby=-id"), contains("low2", "low1", "high"));
        assertThat(searchIds("collections=s&sortby=datetime"), contains("low1", "high", "low2"));
    }

    @Test
    public void testFieldsExclude() throws Exception {
        DocumentContext json = getAsJSONPath("ogc/stac/v1/search?ids=high&fields=-properties.cloud", 200);
        Collection<String> keys = json.read("features[0].properties.keys()");
        assertThat(keys, hasItem("datetime"));
        assertThat(keys, not(hasItem("cloud")));
    }

    @Test
    public void testCQL2JsonOverGet() throws Exception {
        String filter = "{\"op\":\"=\",\"args\":[{\"property\":\"cloud\"},50]}";
        assertThat(searchIds("filter-lang=cql2-json&filter=" + encode(filter)), contains("high"));
    }

    @Test
    public void testLimitClamped() throws Exception {
        PgStacServiceInfo info = getGeoServer().getService(PgStacServiceInfo.class);
        int max = info.getMaxFeatures();
        info.setMaxFeatures(2);
        getGeoServer().save(info);
        try {
            assertThat(searchIds("collections=s&limit=100"), hasSize(2));
        } finally {
            info.setMaxFeatures(max);
            getGeoServer().save(info);
        }
    }

    @Test
    public void testInvalidParameters() throws Exception {
        // reported as client errors, not as server failures
        String reversed = "datetime=2024-01-03T00:00:00Z/2024-01-01T00:00:00Z";
        for (String query : List.of(
                "datetime=not-a-date", "datetime=../..", reversed, "filter=cloud%20%3C", "bbox=1,2,3", "limit=0")) {
            DocumentContext json = getAsJSONPath("ogc/stac/v1/search?" + query, 400);
            assertEquals(query, "InvalidParameterValue", json.read("code"));
            // the PostgreSQL message alone, without the PL/pgSQL call stack
            assertThat(query, json.read("description"), not(containsString("PL/pgSQL")));
        }
    }

    private List<String> searchIds(String query) throws Exception {
        return getAsJSONPath("ogc/stac/v1/search?" + query, 200).read("features[*].id");
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, UTF_8);
    }
}

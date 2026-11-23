/* (c) 2026 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.pgstac;

import static org.geoserver.pgstac.PgStacFilters.and;
import static org.geoserver.pgstac.PgStacFilters.toCQL2Json;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

import java.util.List;
import org.geotools.api.filter.Filter;
import org.geotools.filter.text.ecql.ECQL;
import org.junit.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

public class PgStacFiltersTest {

    private static final JsonMapper MAPPER = new JsonMapper();

    private static final String LOW_CLOUD =
            """
            {"op": "<", "args": [{"property": "eo:cloud_cover"}, 20]}""";

    private static final String PLATFORM =
            """
            {"op": "=", "args": [{"property": "platform"}, "sentinel-2a"]}""";

    @Test
    public void testIncludeHasNoFilter() throws Exception {
        assertNull(toCQL2Json(Filter.INCLUDE));
    }

    @Test
    public void testComparisonsAndLogic() throws Exception {
        assertCQL2(
                "\"eo:cloud_cover\" < 20 AND platform = 'sentinel-2a'",
                """
                {"op": "and", "args": [
                  {"op": "<", "args": [{"property": "eo:cloud_cover"}, 20]},
                  {"op": "=", "args": [{"property": "platform"}, "sentinel-2a"]}]}
                """);
    }

    @Test
    public void testInBetweenLike() throws Exception {
        assertCQL2(
                "platform IN ('sentinel-2a', 'sentinel-2b')",
                """
                {"op": "in", "args": [{"property": "platform"}, ["sentinel-2a", "sentinel-2b"]]}
                """);
        assertCQL2(
                "\"eo:cloud_cover\" BETWEEN 10 AND 20",
                """
                {"op": "between", "args": [{"property": "eo:cloud_cover"}, 10, 20]}
                """);
        assertCQL2(
                "platform LIKE 'sentinel%'",
                """
                {"op": "like", "args": [{"property": "platform"}, "sentinel%"]}
                """);
    }

    @Test
    public void testTemporal() throws Exception {
        assertCQL2(
                "datetime AFTER 2024-01-01T00:00:00Z",
                """
                {"op": "t_after", "args": [{"property": "datetime"}, {"timestamp": "2024-01-01T00:00:00Z"}]}
                """);
    }

    @Test
    public void testSpatial() throws Exception {
        JsonNode json = toCQL2Json(ECQL.toFilter("INTERSECTS(geometry, POINT(10 20))"));
        assertEquals("s_intersects", json.get("op").asString());
        assertEquals("geometry", json.at("/args/0/property").asString());
        assertEquals("Point", json.at("/args/1/type").asString());
    }

    @Test
    public void testUnsupportedConstructs() throws Exception {
        List<String> filters = List.of(
                "DWITHIN(geometry, POINT(10 20), 1000, meters)",
                "BEYOND(geometry, POINT(10 20), 1000, meters)",
                "datetime DURING 2024-01-01T00:00:00Z/2024-02-01T00:00:00Z",
                "\"file:size\" > 3000000000",
                "strToLowerCase(platform) = 'sentinel-2a'",
                "platform ILIKE 'sentinel%'",
                "EXCLUDE");
        for (String ecql : filters) {
            Filter filter = ECQL.toFilter(ecql);
            PgStacException e = assertThrows(ecql, PgStacException.class, () -> toCQL2Json(filter));
            assertEquals(ecql, 400, e.getHttpStatus());
        }
    }

    @Test
    public void testAndIntoRequest() throws Exception {
        ObjectNode request = json("""
                {"collections": ["s2"]}
                """);
        and(request, null);
        assertEquals(json("""
                {"collections": ["s2"]}
                """), request);

        and(request, json(LOW_CLOUD));
        assertEquals(
                json(
                        """
                {"collections": ["s2"], "filter-lang": "cql2-json", "filter": %s}
                """
                                .formatted(LOW_CLOUD)),
                request);

        and(request, json(PLATFORM));
        assertEquals(
                json(
                        """
                {"collections": ["s2"], "filter-lang": "cql2-json",
                 "filter": {"op": "and", "args": [%s, %s]}}
                """
                                .formatted(LOW_CLOUD, PLATFORM)),
                request);
    }

    @Test
    public void testAndRejectsOtherFilterLanguages() {
        List<String> requests = List.of(
                """
                {"filter-lang": "cql-json", "filter": {"eq": [{"property": "platform"}, "sentinel-2a"]}}
                """,
                """
                {"query": {"eo:cloud_cover": {"lt": 20}}}
                """);
        for (String text : requests) {
            ObjectNode request = json(text);
            PgStacException e = assertThrows(text, PgStacException.class, () -> and(request, json(LOW_CLOUD)));
            assertEquals(text, 400, e.getHttpStatus());
        }
    }

    private static ObjectNode json(String text) {
        return (ObjectNode) MAPPER.readTree(text);
    }

    private static void assertCQL2(String ecql, String expected) throws Exception {
        assertEquals(ecql, MAPPER.readTree(expected), toCQL2Json(ECQL.toFilter(ecql)));
    }
}

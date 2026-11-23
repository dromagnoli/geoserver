/* (c) 2026 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.pgstac.stac;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.util.Map;
import org.geoserver.ogcapi.APIFilterParser;
import org.geoserver.pgstac.PgStacException;
import org.geoserver.pgstac.PgStacServiceInfoImpl;
import org.junit.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/** Checks the conversions of STAC search parameters into PgSTAC search requests. */
public class SearchRequestBuilderTest {

    private static final JsonMapper MAPPER = new JsonMapper();

    private final SearchRequestBuilder builder = new SearchRequestBuilder(new APIFilterParser());

    private final PgStacServiceInfoImpl info = new PgStacServiceInfoImpl();

    @Test
    public void testQueryToSearch() throws Exception {
        Map<String, String> params = Map.of(
                "collections", "S2,S1",
                "bbox", "-10,40.5,10,50",
                "datetime", "2024-01-01T00:00:00Z/..",
                "limit", "5000",
                // the + of "+datetime" decoded to a space
                "sortby", " datetime,-eo:cloud_cover",
                "fields", "id,+properties.datetime,-assets");
        JsonNode expected = MAPPER.readTree(
                """
                {"collections": ["S2", "S1"],
                 "bbox": [-10.0, 40.5, 10.0, 50.0],
                 "datetime": "2024-01-01T00:00:00Z/..",
                 "sortby": [{"field": "datetime", "direction": "asc"}, {"field": "eo:cloud_cover", "direction": "desc"}],
                 "fields": {"include": ["id", "properties.datetime"], "exclude": ["assets"]},
                 "limit": 1000,
                 "conf": {"context": "counted"}}
                """);
        assertEquals(expected, builder.fromQuery(params, info));
    }

    @Test
    public void testLimitDefaultsToPageSize() throws Exception {
        info.setSkipNumberMatched(true);
        ObjectNode search = builder.fromBody(MAPPER.createObjectNode(), info);
        assertEquals(10, search.get("limit").intValue());
        assertEquals("off", search.at("/conf/context").asString());
    }

    @Test
    public void testFilterLanguages() throws Exception {
        String json = """
                {"op": "<", "args": [{"property": "gsd"}, 10]}""";
        JsonNode expected = MAPPER.readTree(json);
        ObjectNode body = MAPPER.createObjectNode();
        body.set("filter", expected);
        assertEquals(expected, builder.fromBody(body, info).get("filter"));
        assertEquals(expected, filter(Map.of("filter", json, "filter-lang", "cql2-json")));

        JsonNode text = filter(Map.of("filter", "gsd < 10"));
        assertEquals("<", text.path("op").asString());
        assertEquals("gsd", text.at("/args/0/property").asString());
        assertEquals(
                "<",
                filter(Map.of("filter", "gsd < 10", "filter-lang", "ecql-text"))
                        .path("op")
                        .asString());
    }

    @Test
    public void testRejectedRequests() {
        assertBadRequest(Map.of("query", "{}"));
        assertBadRequest(Map.of("bbox", "1,2,3"));
        assertBadRequest(Map.of("bbox", "1,2,3,east"));
        assertBadRequest(Map.of("limit", "0"));
        assertBadRequest(Map.of("limit", "ten"));
        assertBadRequest(Map.of("intersects", "{not json"));
        assertBadRequest(Map.of("filter", "gsd < 10", "filter-crs", "http://www.opengis.net/def/crs/EPSG/0/3857"));
    }

    private JsonNode filter(Map<String, String> params) throws PgStacException {
        ObjectNode search = builder.fromQuery(params, info);
        assertEquals("cql2-json", search.get("filter-lang").asString());
        return search.get("filter");
    }

    private void assertBadRequest(Map<String, String> params) {
        PgStacException e = assertThrows(PgStacException.class, () -> builder.fromQuery(params, info));
        assertEquals(400, e.getHttpStatus());
    }
}

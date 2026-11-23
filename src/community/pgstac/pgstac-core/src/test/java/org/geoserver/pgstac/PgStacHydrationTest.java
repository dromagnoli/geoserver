/* (c) 2026 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.pgstac;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

public class PgStacHydrationTest {

    private static final JsonMapper MAPPER = new JsonMapper();

    @Test
    public void testHydrate() {
        // the marker is the PgSTAC do-not-merge string, U+1235F U+203B
        String item =
                """
                {"id": "i1", "collection": "c", "type": "Feature",
                 "properties": {"datetime": "2024-01-15T00:00:00Z", "end_datetime": null},
                 "assets": {"data": {"href": "https://x/d.tif"}, "thumb": "\\ud808\\udf5f\\u203b"},
                 "bands": [{"name": "B04"}, {"name": "B03"}],
                 "keywords": ["a"],
                 "pgstac:base_item": {"type": "Feature", "stac_version": "1.0.0", "collection": "c",
                   "assets": {"data": {"type": "image/tiff", "roles": ["data"]}, "thumb": {"type": "image/png"}},
                   "bands": [{"unit": "m"}, {"unit": "m"}],
                   "keywords": ["a", "b"]}}
                """;
        // same-size arrays merge by position, the others keep the item one
        String expected =
                """
                {"id": "i1", "collection": "c", "type": "Feature", "stac_version": "1.0.0",
                 "properties": {"datetime": "2024-01-15T00:00:00Z"},
                 "assets": {"data": {"href": "https://x/d.tif", "type": "image/tiff", "roles": ["data"]}},
                 "bands": [{"name": "B04", "unit": "m"}, {"name": "B03", "unit": "m"}],
                 "keywords": ["a"]}
                """;
        assertHydrated(item, null, expected);
    }

    @Test
    public void testHydrateWithFields() {
        String item =
                """
                {"id": "i1", "collection": "c", "type": "Feature",
                 "properties": {"datetime": "2024-01-15T00:00:00Z", "eo:cloud_cover": 5},
                 "assets": {"data": {"href": "https://x/d.tif"}},
                 "pgstac:base_item": {"type": "Feature", "collection": "c",
                   "assets": {"data": {"roles": ["data"]}}}}
                """;
        String fields =
                """
                {"include": ["properties.eo:cloud_cover", "assets.data"], "exclude": ["assets.data.href"]}
                """;
        // id and collection are always included, the projection applies to the base item too
        String expected =
                """
                {"id": "i1", "collection": "c",
                 "properties": {"eo:cloud_cover": 5},
                 "assets": {"data": {"roles": ["data"]}}}
                """;
        assertHydrated(item, fields, expected);
    }

    private static void assertHydrated(String item, String fields, String expected) {
        ObjectNode page = MAPPER.createObjectNode();
        page.putArray("features").add(MAPPER.readTree(item));
        JsonNode fieldsNode = fields == null ? null : MAPPER.readTree(fields);
        PgStacHydration.hydrate(page, fieldsNode);
        assertEquals(MAPPER.readTree(expected), page.path("features").get(0));
    }
}

/* (c) 2026 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.pgstac.rest;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItems;
import static org.junit.Assert.assertEquals;

import com.jayway.jsonpath.JsonPath;
import java.util.List;
import org.geoserver.data.test.SystemTestData;
import org.geoserver.pgstac.DefaultPgStacFacade;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

public class QueryablesControllerTest extends PgStacRestTestSupport {

    private static final String COLLECTION =
            """
            {"type": "Collection", "id": "c1", "stac_version": "1.0.0", "description": "test", "license": "proprietary",
             "links": [],
             "extent": {"spatial": {"bbox": [[-180, -90, 180, 90]]},
                        "temporal": {"interval": [["2024-01-01T00:00:00Z", null]]}}}
            """;

    @Override
    protected void onSetUp(SystemTestData testData) throws Exception {
        super.onSetUp(testData);
        ObjectNode collection = (ObjectNode) new JsonMapper().readTree(COLLECTION);
        applicationContext.getBean(DefaultPgStacFacade.class).createCollection(collection);
    }

    @Test
    public void testLifecycle() throws Exception {
        String body = """
                {"definition": {"type": "number"}, "property_index_type": "BTREE"}""";
        assertEquals(200, putJson("eo:cloud_cover", body).getStatus());

        String json = getAsString(REST + "queryables/eo:cloud_cover");
        assertEquals("number", JsonPath.read(json, "$.queryables[0].definition.type"));
        assertEquals("BTREE", JsonPath.read(json, "$.queryables[0].property_index_type"));
        List<String> names = JsonPath.read(getAsString(REST + "queryables"), "$.queryables[*].name");
        assertThat(names, hasItems("id", "eo:cloud_cover"));

        assertEquals(
                200, deleteAsServletResponse(REST + "queryables/eo:cloud_cover").getStatus());
        assertError(
                404, "Queryable eo:cloud_cover not found", getAsServletResponse(REST + "queryables/eo:cloud_cover"));
    }

    @Test
    public void testPerCollection() throws Exception {
        assertEquals(
                200,
                putJson("sat:orbit_state", "{\"collection_ids\": [\"c1\"]}").getStatus());
        String json = getAsString(REST + "queryables/sat:orbit_state");
        assertEquals(List.of("c1"), JsonPath.read(json, "$.queryables[0].collection_ids"));

        // without collections the delete targets the global row, which does not exist
        String notFound = "Queryable sat:orbit_state for all collections not found";
        assertError(404, notFound, deleteAsServletResponse(REST + "queryables/sat:orbit_state"));
        String perCollection = REST + "queryables/sat:orbit_state?collections=c1";
        assertEquals(200, deleteAsServletResponse(perCollection).getStatus());
    }

    @Test
    public void testInvalidBody() throws Exception {
        assertError(400, "do not exist", putJson("q1", "{\"collection_ids\": [\"missing\"]}"));
        assertError(400, "is not in queryable_wrappers", putJson("q1", "{\"property_wrapper\": \"nope\"}"));
        assertError(400, "must be a JSON object", putJson("q1", "[]"));
        assertError(400, "not valid JSON", putJson("q1", "{"));
    }

    private MockHttpServletResponse putJson(String name, String body) throws Exception {
        return putAsServletResponse(REST + "queryables/" + name, body, "application/json");
    }

    private static void assertError(int status, String message, MockHttpServletResponse response) throws Exception {
        String body = response.getContentAsString();
        assertEquals(body, status, response.getStatus());
        assertThat(body, containsString(message));
    }
}

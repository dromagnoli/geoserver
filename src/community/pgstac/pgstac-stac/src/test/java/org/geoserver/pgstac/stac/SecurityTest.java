/* (c) 2026 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.pgstac.stac;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.junit.Assert.assertEquals;

import java.util.List;
import org.geoserver.pgstac.AccessRule;
import org.geoserver.pgstac.PgStacFacade;
import org.geoserver.pgstac.PgStacServiceInfo;
import org.junit.After;
import org.junit.Test;

/** Checks the access rules through every endpoint: what a role does not grant is missing or 404, never leaked. */
public class SecurityTest extends PgStacApiTestSupport {

    private static final String ITEM =
            """
            {"type": "Feature", "stac_version": "1.0.0", "id": "%s", "collection": "%s", "links": [], "assets": {},
             "geometry": {"type": "Point", "coordinates": [10, 20]}, "bbox": [10, 20, 10, 20],
             "properties": {"datetime": "2024-01-15T00:00:00Z", "eo:cloud_cover": %d}}
            """;

    @Override
    protected void configure(PgStacServiceInfo info) {
        // id is an ECQL keyword (feature id filters), so the property must be quoted
        info.getCollectionAccessRules().add(rule(null, "\"id\" = 'restricted'", "ROLE_S2"));
        info.getItemAccessRules().add(rule("open", "\"eo:cloud_cover\" >= 20", "ROLE_FULL"));
    }

    private static AccessRule rule(String collection, String cqlFilter, String role) {
        AccessRule rule = new AccessRule();
        rule.setCollection(collection);
        rule.setCqlFilter(cqlFilter);
        rule.getRoles().add(role);
        return rule;
    }

    @Override
    protected void loadData(PgStacFacade facade) throws Exception {
        for (String collection : List.of("open", "restricted")) facade.createCollection(json(COLLECTION, collection));
        facade.createItem(json(ITEM, "clear", "open", 5));
        facade.createItem(json(ITEM, "cloudy", "open", 50));
        facade.createItem(json(ITEM, "r1", "restricted", 5));
    }

    @After
    public void cleanUp() {
        logout();
    }

    @Test
    public void testAnonymous() throws Exception {
        assertThat(collectionIds(), contains("open"));
        assertThat(searchIds(""), contains("clear"));
        assertThat(searchIds("collections=restricted"), empty());
        assertThat(searchIds("ids=cloudy,r1"), empty());
        assertThat(itemIds("open"), contains("clear"));
        for (String path : List.of(
                "collections/restricted",
                "collections/restricted/items",
                "collections/restricted/items/r1",
                "collections/open/items/cloudy",
                "collections/restricted/queryables",
                "collections/restricted/sortables")) {
            getAsJSONPath("ogc/stac/v1/" + path, 404);
        }
        // the HTML pages go through the same checks
        assertEquals(
                404,
                getAsServletResponse("ogc/stac/v1/collections/restricted?f=text/html")
                        .getStatus());
    }

    @Test
    public void testCollectionRole() throws Exception {
        login("s2", "pwd", "ROLE_S2");
        assertThat(collectionIds(), containsInAnyOrder("open", "restricted"));
        assertThat(searchIds(""), containsInAnyOrder("clear", "r1"));
        assertThat(itemIds("restricted"), contains("r1"));
        getAsJSONPath("ogc/stac/v1/collections/restricted/items/r1", 200);
        // the collection role does not grant the item rule
        getAsJSONPath("ogc/stac/v1/collections/open/items/cloudy", 404);
    }

    @Test
    public void testItemRole() throws Exception {
        login("full", "pwd", "ROLE_FULL");
        assertThat(searchIds(""), containsInAnyOrder("clear", "cloudy"));
        assertThat(itemIds("open"), containsInAnyOrder("clear", "cloudy"));
        getAsJSONPath("ogc/stac/v1/collections/open/items/cloudy", 200);
        getAsJSONPath("ogc/stac/v1/collections/restricted", 404);
    }

    @Test
    public void testAdmin() throws Exception {
        loginAsAdmin();
        assertThat(collectionIds(), containsInAnyOrder("open", "restricted"));
        assertThat(searchIds(""), containsInAnyOrder("clear", "cloudy", "r1"));
    }

    private List<String> collectionIds() throws Exception {
        return getAsJSONPath("ogc/stac/v1/collections", 200).read("collections[*].id");
    }

    private List<String> searchIds(String query) throws Exception {
        return getAsJSONPath("ogc/stac/v1/search?" + query, 200).read("features[*].id");
    }

    private List<String> itemIds(String collection) throws Exception {
        return getAsJSONPath("ogc/stac/v1/collections/" + collection + "/items", 200)
                .read("features[*].id");
    }
}

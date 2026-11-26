/* (c) 2026 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.pgstac.stac;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.junit.Assert.assertEquals;

import com.jayway.jsonpath.DocumentContext;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import javax.sql.DataSource;
import org.geoserver.pgstac.PgStacConnectionManager;
import org.geoserver.pgstac.PgStacFacade;
import org.junit.Test;

/** Checks the sortables are the queryables with an order, and that sorting on them follows their type. */
public class SortablesTest extends PgStacApiTestSupport {

    private static final String ITEM =
            """
            {"type": "Feature", "stac_version": "1.0.0", "id": "%s", "collection": "a", "links": [], "assets": {},
             "geometry": {"type": "Point", "coordinates": [10, 20]}, "bbox": [10, 20, 10, 20],
             "properties": {"datetime": "2024-01-01T00:00:00Z", "eo:cloud_cover": %d}}
            """;

    @Override
    protected void loadData(PgStacFacade facade) throws Exception {
        for (String collection : List.of("a", "b")) facade.createCollection(json(COLLECTION, collection));
        DataSource dataSource =
                applicationContext.getBean(PgStacConnectionManager.class).getDataSource();
        try (Connection c = dataSource.getConnection();
                Statement st = c.createStatement()) {
            st.execute("SELECT pgstac.upsert_queryable('eo:cloud_cover', '{\"type\": \"number\"}')");
            st.execute("SELECT pgstac.upsert_queryable('level', '{\"type\": \"integer\"}', collection_ids => '{a}')");
            st.execute("SELECT pgstac.upsert_queryable('tags', '{\"type\": \"array\"}', collection_ids => '{a}')");
        }
        // as text, 10 would sort before 5
        facade.createItem(json(ITEM, "cloudy", 50));
        facade.createItem(json(ITEM, "clear", 5));
        facade.createItem(json(ITEM, "hazy", 10));
    }

    @Test
    public void testSortables() throws Exception {
        DocumentContext global = getAsJSONPath("ogc/stac/v1/sortables", 200);
        assertEquals(BASE + "sortables", global.read("$['$id']"));
        assertEquals("Sortables", global.read("title"));
        // every collection's queryables, geometry and the array dropped
        assertThat(global.read("properties.keys()"), containsInAnyOrder("id", "datetime", "eo:cloud_cover", "level"));

        DocumentContext collection = getAsJSONPath("ogc/stac/v1/collections/b/sortables", 200);
        assertEquals(BASE + "collections/b/sortables", collection.read("$['$id']"));
        assertThat(collection.read("properties.keys()"), containsInAnyOrder("id", "datetime", "eo:cloud_cover"));

        getAsJSONPath("ogc/stac/v1/collections/missing/sortables", 404);
    }

    @Test
    public void testLandingPageLink() throws Exception {
        DocumentContext json = getAsJSONPath("ogc/stac/v1", 200);
        assertThat(json.read("links[*].href"), hasItem(BASE + "sortables"));
    }

    @Test
    public void testSortByNumber() throws Exception {
        DocumentContext json = getAsJSONPath("ogc/stac/v1/search?collections=a&sortby=eo:cloud_cover", 200);
        assertThat(json.read("features[*].id"), contains("clear", "hazy", "cloudy"));
    }
}

/* (c) 2026 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.pgstac;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.util.ArrayList;
import java.util.List;
import org.geoserver.config.GeoServer;
import org.geoserver.data.test.SystemTestData;
import org.geoserver.test.GeoServerSystemTestSupport;
import org.geotools.filter.text.ecql.ECQL;
import org.junit.ClassRule;
import org.junit.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/** Runs the facade against a real PgSTAC 0.10.1 database, configured through the GeoServer service settings. */
public class DefaultPgStacFacadeIntegrationTest extends GeoServerSystemTestSupport {

    @ClassRule
    public static final PgStacTestResource PGSTAC = new PgStacTestResource();

    private static final JsonMapper MAPPER = new JsonMapper();

    private static final String COLLECTION =
            """
            {"type": "Collection", "id": "s2", "stac_version": "1.0.0", "description": "Sentinel-2",
             "license": "proprietary", "links": [],
             "extent": {"spatial": {"bbox": [[-180, -90, 180, 90]]},
                        "temporal": {"interval": [["2024-01-01T00:00:00Z", null]]}},
             "_geoserver": {"workspaces": ["internal"]}}
            """;

    // item_assets become the base item assets, so the items below are stored without what they share with it
    private static final String HYDRATION_COLLECTION =
            """
            {"type": "Collection", "id": "hydration", "stac_version": "1.0.0", "description": "test",
             "license": "proprietary", "links": [],
             "extent": {"spatial": {"bbox": [[-180, -90, 180, 90]]},
                        "temporal": {"interval": [["2024-01-01T00:00:00Z", null]]}},
             "item_assets": {"data": {"type": "image/tiff", "roles": ["data"], "title": "%s"},
                             "thumb": {"type": "image/png", "roles": ["thumbnail"]}}}
            """;

    // no thumb asset and no data title: PgSTAC stores do-not-merge markers for them
    private static final String HYDRATION_ITEM =
            """
            {"type": "Feature", "stac_version": "1.0.0", "id": "%s", "collection": "hydration", "links": [],
             "geometry": {"type": "Point", "coordinates": [10, 20]}, "bbox": [10, 20, 10, 20],
             "properties": {"datetime": "2024-01-15T00:00:00Z", "eo:cloud_cover": 5},
             "assets": {"data": {"href": "https://example.com/d.tif", "type": "image/tiff", "roles": ["data"]}}}
            """;

    @Override
    protected void setUpTestData(SystemTestData testData) throws Exception {
        // no catalog layers needed, only the security setup
        testData.setUpSecurity();
    }

    @Override
    protected void onSetUp(SystemTestData testData) throws Exception {
        GeoServer gs = getGeoServer();
        PgStacServiceInfo info = gs.getService(PgStacServiceInfo.class);
        info.setConnectionParameters(PGSTAC.getConnectionParameters());
        gs.save(info);

        PgStacFacade facade = facade();
        facade.createCollection(json(COLLECTION));
        facade.createItem(item("low-cloud", 5, "sentinel-2a", "2024-01-15T00:00:00Z"));
        facade.createItem(item("cloudy", 50, "sentinel-2b", "2024-02-15T00:00:00Z"));
    }

    @Test
    public void testVersion() throws Exception {
        assertThat(facade().getVersion(), startsWith("0.10."));
    }

    @Test
    public void testGetItemKeepsGeoServerMetadata() throws Exception {
        ObjectNode item = facade().getItem("s2", "low-cloud");
        assertEquals(
                "/mnt/low-cloud.tif",
                item.at("/assets/data/_geoserver/location").asString());
    }

    @Test
    public void testSearchWithConvertedFilter() throws Exception {
        String ecql = "\"eo:cloud_cover\" < 20 AND platform IN ('sentinel-2a', 'sentinel-2c')"
                + " AND datetime AFTER 2024-01-01T00:00:00Z";
        ObjectNode request = MAPPER.createObjectNode();
        request.putArray("collections").add("s2");
        request.set("filter", PgStacFilters.toCQL2Json(ECQL.toFilter(ecql)));
        request.put("filter-lang", "cql2-json");

        assertThat(featureIds(facade().search(request)), contains("low-cloud"));
    }

    @Test
    public void testUpdateKeepsGeoServerMetadata() throws Exception {
        PgStacFacade facade = facade();
        facade.createItem(item("updated", 30, "sentinel-2a", "2024-03-15T00:00:00Z"));

        // what a client sends back after a GET: the stripped item with a changed property
        ObjectNode update = item("updated", 10, "sentinel-2a", "2024-03-15T00:00:00Z");
        GeoServerMetadata.strip(update);
        facade.updateItem(update);

        ObjectNode stored = facade.getItem("s2", "updated");
        assertEquals(10, stored.at("/properties/eo:cloud_cover").asInt());
        assertEquals(
                "/mnt/updated.tif",
                stored.at("/assets/data/_geoserver/location").asString());
    }

    @Test
    public void testCollectionPrivate() throws Exception {
        PgStacFacade facade = facade();
        ObjectNode layers = json(
                """
                {"geoserver": {"defaultLayer": {"workspace": "internal", "layer": "s2_rgb"}}}
                """);
        facade.setCollectionPrivate("s2", layers);

        assertEquals(layers, facade.getCollectionPrivate("s2"));
    }

    @Test
    public void testDuplicateIsConflict() {
        PgStacException e = assertThrows(PgStacException.class, () -> facade().createCollection(json(COLLECTION)));
        assertEquals(409, e.getHttpStatus());
    }

    @Test
    public void testInvalidFilterIsBadRequest() {
        // 0.10 requires a list as second argument of "in"
        ObjectNode request = json(
                """
                {"collections": ["s2"], "filter-lang": "cql2-json",
                 "filter": {"op": "in", "args": [{"property": "platform"}, "sentinel-2a"]}}
                """);
        PgStacException e = assertThrows(PgStacException.class, () -> facade().search(request));
        assertEquals(400, e.getHttpStatus());
    }

    @Test
    public void testApiHydrationMatchesDatabase() throws Exception {
        PgStacFacade facade = facade();
        facade.createCollection(json(HYDRATION_COLLECTION.formatted("Data")));
        facade.createItem(json(HYDRATION_ITEM.formatted("h1")));
        // h2 is dehydrated against a newer base item than h1
        facade.updateCollection(json(HYDRATION_COLLECTION.formatted("Data v2")));
        facade.createItem(json(HYDRATION_ITEM.formatted("h2")));

        assertSameHydration(null);
        assertSameHydration(
                """
                {"include": ["properties.eo:cloud_cover", "assets.data"], "exclude": ["assets.data.href"]}
                """);
        assertSameHydration("""
                {"exclude": ["geometry", "properties"]}
                """);
    }

    private void assertSameHydration(String fields) throws Exception {
        ObjectNode request =
                json("""
                {"collections": ["hydration"], "ids": ["h1", "h2"]}
                """);
        if (fields != null) request.set("fields", json(fields));
        JsonNode inDatabase = facade().search(request.deepCopy()).path("features");
        assertEquals(fields, 2, inDatabase.size());

        setUseApiHydrate(true);
        try {
            assertEquals(fields, inDatabase, facade().search(request.deepCopy()).path("features"));
        } finally {
            setUseApiHydrate(false);
        }
    }

    private void setUseApiHydrate(boolean useApiHydrate) {
        GeoServer gs = getGeoServer();
        PgStacServiceInfo info = gs.getService(PgStacServiceInfo.class);
        info.setUseApiHydrate(useApiHydrate);
        gs.save(info);
    }

    private PgStacFacade facade() {
        // the unsecured one, the primary bean is SecurePgStacFacade
        return applicationContext.getBean(DefaultPgStacFacade.class);
    }

    private static ObjectNode item(String id, int cloudCover, String platform, String datetime) {
        return json(
                """
                {"type": "Feature", "stac_version": "1.0.0", "id": "%s", "collection": "s2", "links": [],
                 "geometry": {"type": "Point", "coordinates": [10, 20]}, "bbox": [10, 20, 10, 20],
                 "properties": {"datetime": "%s", "eo:cloud_cover": %d, "platform": "%s"},
                 "assets": {"data": {"href": "https://example.com/%s.tif",
                                     "_geoserver": {"location": "/mnt/%s.tif"}}}}
                """
                        .formatted(id, datetime, cloudCover, platform, id, id));
    }

    private static List<String> featureIds(JsonNode featureCollection) {
        List<String> ids = new ArrayList<>();
        for (JsonNode feature : featureCollection.path("features")) {
            ids.add(feature.path("id").asString());
        }
        return ids;
    }

    private static ObjectNode json(String text) {
        return (ObjectNode) MAPPER.readTree(text);
    }
}

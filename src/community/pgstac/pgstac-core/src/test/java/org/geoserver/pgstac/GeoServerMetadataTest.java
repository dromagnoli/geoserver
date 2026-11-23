/* (c) 2026 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.pgstac;

import static org.geoserver.pgstac.GeoServerMetadata.graft;
import static org.geoserver.pgstac.GeoServerMetadata.strip;
import static org.junit.Assert.assertEquals;

import org.junit.Test;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

public class GeoServerMetadataTest {

    private static final JsonMapper MAPPER = new JsonMapper();

    @Test
    public void testStripCollection() {
        ObjectNode collection = json(
                """
                {"id": "s2", "title": "Sentinel-2", "_geoserver": {"workspaces": ["internal"]}}
                """);
        strip(collection);
        assertEquals(json("""
                {"id": "s2", "title": "Sentinel-2"}
                """), collection);
    }

    @Test
    public void testStripItemAssets() {
        ObjectNode item = json(
                """
                {"id": "i1", "assets": {
                  "B04": {"href": "https://x/B04.tif", "_geoserver": {"location": "/mnt/B04.tif"}},
                  "thumbnail": {"href": "https://x/t.png"}}}
                """);
        strip(item);
        assertEquals(
                json(
                        """
                {"id": "i1", "assets": {
                  "B04": {"href": "https://x/B04.tif"},
                  "thumbnail": {"href": "https://x/t.png"}}}
                """),
                item);
    }

    @Test
    public void testStripPages() {
        ObjectNode items = json(
                """
                {"type": "FeatureCollection", "features": [
                  {"id": "i1", "assets": {"data": {"href": "a", "_geoserver": {"location": "/mnt/a"}}}}]}
                """);
        ObjectNode collections = json(
                """
                {"collections": [{"id": "s2", "_geoserver": {"workspaces": ["internal"]}}]}
                """);
        strip(items);
        strip(collections);
        assertEquals(
                json(
                        """
                {"type": "FeatureCollection", "features": [{"id": "i1", "assets": {"data": {"href": "a"}}}]}
                """),
                items);
        assertEquals(json("""
                {"collections": [{"id": "s2"}]}
                """), collections);
    }

    @Test
    public void testGraftCollection() {
        ObjectNode stored = json(
                """
                {"id": "s2", "title": "old", "_geoserver": {"workspaces": ["internal"]}}
                """);
        ObjectNode incoming = json("""
                {"id": "s2", "title": "new"}
                """);
        graft(stored, incoming);
        assertEquals(
                json(
                        """
                {"id": "s2", "title": "new", "_geoserver": {"workspaces": ["internal"]}}
                """),
                incoming);
    }

    @Test
    public void testGraftItemAssetsByKey() {
        ObjectNode stored = json(
                """
                {"id": "i1", "assets": {
                  "B04": {"href": "https://x/B04.tif", "_geoserver": {"location": "/mnt/B04.tif"}},
                  "B03": {"href": "https://x/B03.tif", "_geoserver": {"location": "/mnt/B03.tif"}}}}
                """);
        ObjectNode incoming = json(
                """
                {"id": "i1", "assets": {
                  "B04": {"href": "https://y/B04.tif"},
                  "B02": {"href": "https://y/B02.tif"}}}
                """);
        graft(stored, incoming);
        // B04 keeps its location, B03 was removed by the update and is not re-added, B02 is new
        assertEquals(
                json(
                        """
                {"id": "i1", "assets": {
                  "B04": {"href": "https://y/B04.tif", "_geoserver": {"location": "/mnt/B04.tif"}},
                  "B02": {"href": "https://y/B02.tif"}}}
                """),
                incoming);
    }

    @Test
    public void testIncomingMetadataWins() {
        ObjectNode stored = json(
                """
                {"id": "i1", "_geoserver": {"workspaces": ["internal"]},
                 "assets": {"data": {"href": "a", "_geoserver": {"location": "/mnt/old"}}}}
                """);
        ObjectNode incoming = json(
                """
                {"id": "i1", "_geoserver": {"workspaces": ["public"]},
                 "assets": {"data": {"href": "a", "_geoserver": {"location": "/mnt/new"}}}}
                """);
        ObjectNode expected = incoming.deepCopy();
        graft(stored, incoming);
        assertEquals(expected, incoming);
    }

    @Test
    public void testGraftWithoutStoredObject() {
        ObjectNode incoming =
                json("""
                {"id": "i1", "assets": {"data": {"href": "a"}}}
                """);
        ObjectNode expected = incoming.deepCopy();
        graft(null, incoming);
        assertEquals(expected, incoming);
    }

    private static ObjectNode json(String text) {
        return (ObjectNode) MAPPER.readTree(text);
    }
}

/* (c) 2026 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.pgstac;

import java.util.Map;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * The reserved {@code _geoserver} entries GeoServer keeps inside the stored STAC JSON, top-level on collections and
 * inside each asset on items; they are never part of the STAC API output.
 */
public class GeoServerMetadata {

    /** Name of the reserved entry. */
    public static final String KEY = "_geoserver";

    private GeoServerMetadata() {}

    /** Removes the {@code _geoserver} entries, in place, from a collection, an item, or a page of either. */
    public static void strip(ObjectNode node) {
        node.remove(KEY);
        removeFromEach(node.get("assets"));
        stripEach(node.get("features"));
        stripEach(node.get("collections"));
    }

    private static void stripEach(JsonNode objects) {
        if (objects == null) return;
        for (JsonNode object : objects) {
            if (object instanceof ObjectNode o) strip(o);
        }
    }

    private static void removeFromEach(JsonNode assets) {
        if (assets == null) return;
        for (JsonNode asset : assets) {
            if (asset instanceof ObjectNode o) o.remove(KEY);
        }
    }

    /**
     * Copies the stored {@code _geoserver} entries into {@code incoming} node, in place, wherever {@code incoming} has
     * none at the same position (top-level, or same asset key); assets missing from {@code incoming} are not re-added.
     */
    public static void graft(ObjectNode stored, ObjectNode incoming) {
        if (stored == null) return; // nothing stored yet, the update itself reports the missing object
        copyIfMissing(stored, incoming);
        JsonNode storedAssets = stored.path("assets");
        JsonNode incomingAssets = incoming.path("assets");
        for (Map.Entry<String, JsonNode> entry : storedAssets.properties()) {
            if (entry.getValue() instanceof ObjectNode from
                    && incomingAssets.get(entry.getKey()) instanceof ObjectNode to) {
                copyIfMissing(from, to);
            }
        }
    }

    private static void copyIfMissing(ObjectNode from, ObjectNode to) {
        if (from.has(KEY) && !to.has(KEY)) to.set(KEY, from.get(KEY));
    }
}

/* (c) 2026 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.pgstac;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.NullNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Hydrates the items of a PgSTAC {@code conf.nohydrate} search page in Java, producing the same JSON PgSTAC 0.10
 * returns when it hydrates them itself ({@code content_hydrate}).
 */
public class PgStacHydration {

    /** Key of the base item PgSTAC attaches to every item it returns dehydrated. */
    static final String BASE_ITEM = "pgstac:base_item";

    // "U+1235F U+203B", written as a surrogate pair (a code point above U+FFFF stored as two chars): marks an entry of
    // the base item the dehydrated item does not have
    private static final String DO_NOT_MERGE = "\uD808\uDF5F\u203B";

    private PgStacHydration() {}

    /**
     * Hydrates, in place, every feature of the page against the base item it carries, applying the search
     * {@code fields} the way PgSTAC does.
     *
     * @param fields the {@code fields} of the search request, {@code null} to keep every field
     */
    public static void hydrate(ObjectNode page, JsonNode fields) {
        if (!(page.get("features") instanceof ArrayNode features)) return;
        for (int i = 0; i < features.size(); i++) {
            if (features.get(i) instanceof ObjectNode item) features.set(i, hydrateItem(item, fields));
        }
    }

    static JsonNode hydrateItem(ObjectNode item, JsonNode fields) {
        JsonNode base = item.remove(BASE_ITEM);
        return merge(applyFields(item, fields), applyFields(base, fields));
    }

    /**
     * Port of PgSTAC {@code merge_jsonb}: {@code a} wins, objects merge by key, same-size arrays by position, object
     * entries that end up null are dropped, and the do-not-merge marker removes the base entry.
     *
     * @return the merged value, {@code null} when the entry must be dropped
     */
    static JsonNode merge(JsonNode a, JsonNode b) {
        if (a != null && a.isString() && DO_NOT_MERGE.equals(a.asString())) return null;
        if (a == null || a.isNull()) return b;
        if (b == null) return a;
        if (a instanceof ObjectNode ao && b instanceof ObjectNode bo) return mergeObjects(ao, bo);
        if (a instanceof ArrayNode aa && b instanceof ArrayNode ba && aa.size() == ba.size()) {
            ArrayNode merged = aa.arrayNode();
            for (int i = 0; i < aa.size(); i++) {
                JsonNode value = merge(aa.get(i), ba.get(i));
                merged.add(value == null ? NullNode.getInstance() : value);
            }
            return merged;
        }
        return a;
    }

    private static ObjectNode mergeObjects(ObjectNode a, ObjectNode b) {
        Set<String> keys = new LinkedHashSet<>(a.propertyNames());
        keys.addAll(b.propertyNames());
        ObjectNode merged = a.objectNode();
        for (String key : keys) {
            JsonNode value = merge(a.get(key), b.get(key));
            // PgSTAC runs jsonb_strip_nulls on every merged object, which also cleans the unmerged subtrees
            if (value != null && !value.isNull()) merged.set(key, stripNulls(value));
        }
        return merged;
    }

    /** Removes, in place and recursively, the object entries holding null; null array elements stay. */
    private static JsonNode stripNulls(JsonNode node) {
        if (node instanceof ObjectNode object) {
            object.remove(object.properties().stream()
                    .filter(e -> e.getValue().isNull())
                    .map(Map.Entry::getKey)
                    .toList());
        }
        for (JsonNode child : node) {
            stripNulls(child);
        }
        return node;
    }

    /**
     * Port of PgSTAC {@code jsonb_fields}: keeps the dotted {@code include} paths, plus {@code id} and
     * {@code collection}, as null when missing, then removes the dotted {@code exclude} paths.
     */
    static JsonNode applyFields(JsonNode json, JsonNode fields) {
        if (!(json instanceof ObjectNode object) || fields == null) return json;
        ObjectNode result = include(object, fields.path("include"));
        for (JsonNode path : fields.path("exclude")) {
            remove(result, path.asString().split("\\."));
        }
        return result;
    }

    private static ObjectNode include(ObjectNode json, JsonNode includes) {
        if (includes.isEmpty()) return json;
        List<String> paths = new ArrayList<>();
        includes.forEach(path -> paths.add(path.asString()));
        paths.add("id");
        if (json.has("collection")) paths.add("collection");

        ObjectNode result = json.objectNode();
        for (String path : paths) {
            set(result, path.split("\\."), get(json, path.split("\\.")));
        }
        return result;
    }

    private static JsonNode get(JsonNode json, String[] path) {
        JsonNode node = json;
        for (String key : path) {
            node = node.get(key);
            if (node == null) return NullNode.getInstance();
        }
        return node;
    }

    /** Sets the value at the path, creating the missing parents as PgSTAC {@code jsonb_set_nested} does. */
    private static void set(ObjectNode json, String[] path, JsonNode value) {
        ObjectNode parent = json;
        for (int i = 0; i < path.length - 1; i++) {
            JsonNode child = parent.get(path[i]);
            if (child == null) child = parent.putObject(path[i]);
            if (!(child instanceof ObjectNode object)) return;
            parent = object;
        }
        parent.set(path[path.length - 1], value);
    }

    private static void remove(JsonNode json, String[] path) {
        JsonNode parent = json;
        for (int i = 0; i < path.length - 1 && parent != null; i++) {
            parent = parent.get(path[i]);
        }
        if (parent instanceof ObjectNode object) object.remove(path[path.length - 1]);
    }
}

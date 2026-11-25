/* (c) 2026 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.pgstac.stac;

import static org.geoserver.ogcapi.APIFilterParser.CQL2_JSON;

import java.util.List;
import java.util.Map;
import org.geoserver.ogcapi.APIFilterParser;
import org.geoserver.pgstac.PgStacException;
import org.geoserver.pgstac.PgStacFilters;
import org.geoserver.pgstac.PgStacServiceInfo;
import org.geotools.api.referencing.FactoryException;
import org.geotools.referencing.CRS;
import org.geotools.referencing.crs.DefaultGeographicCRS;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.IntNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Turns STAC item search requests, GET parameters or POST bodies, into PgSTAC {@code search()} requests, failing with a
 * 400 {@link PgStacException} on malformed or unsupported parameters.
 */
@Component
public class SearchRequestBuilder {

    static final String CRS84 = "http://www.opengis.net/def/crs/OGC/1.3/CRS84";

    private static final JsonMapper MAPPER = new JsonMapper();

    /** POST members PgSTAC checks itself, copied as they are. */
    private static final List<String> PASSTHROUGH =
            List.of("collections", "ids", "bbox", "intersects", "datetime", "token", "sortby", "fields");

    /** GET parameters whose value is already the POST one. */
    private static final List<String> TEXT_PARAMS =
            List.of("datetime", "token", "filter", "filter-lang", "filter-crs", "query");

    private final APIFilterParser filterParser;

    public SearchRequestBuilder(APIFilterParser filterParser) {
        this.filterParser = filterParser;
    }

    /** Builds the search for the GET parameters of STAC item search. */
    public ObjectNode fromQuery(Map<String, String> params, PgStacServiceInfo info) throws PgStacException {
        ObjectNode body = MAPPER.createObjectNode();
        putList(body, "collections", params.get("collections"));
        putList(body, "ids", params.get("ids"));
        for (String name : TEXT_PARAMS) {
            if (params.containsKey(name)) body.put(name, params.get(name));
        }
        String bbox = params.get("bbox");
        if (bbox != null) body.set("bbox", bbox(bbox));
        String intersects = params.get("intersects");
        if (intersects != null) body.set("intersects", readJson("intersects", intersects));
        String limit = params.get("limit");
        if (limit != null) body.put("limit", parseInt("limit", limit));
        String sortBy = params.get("sortby");
        if (sortBy != null) body.set("sortby", sortBy(sortBy));
        String fields = params.get("fields");
        if (fields != null) body.set("fields", fields(fields));
        return fromBody(body, info);
    }

    /** Builds the search for a STAC item search POST body, which is left untouched. */
    public ObjectNode fromBody(ObjectNode body, PgStacServiceInfo info) throws PgStacException {
        if (body.has("query")) throw badRequest("The query extension is not supported, use filter instead");
        ObjectNode search = MAPPER.createObjectNode();
        for (String name : PASSTHROUGH) {
            if (body.has(name)) search.set(name, body.get(name));
        }
        checkBbox(search);
        search.put("limit", limit(body.get("limit"), info));
        putFilter(search, body);
        search.putObject("conf").put("context", info.isSkipNumberMatched() ? "off" : "counted");
        return search;
    }

    /** Builds the PgSTAC {@code collection_search()} request for the paging parameters of GET /collections. */
    public ObjectNode fromCollectionsQuery(Map<String, String> params, PgStacServiceInfo info) throws PgStacException {
        String limit = params.get("limit");
        String offset = params.get("offset");
        int start = offset == null ? 0 : parseInt("offset", offset);
        if (start < 0) throw badRequest("Invalid offset " + offset + ", must not be negative");
        ObjectNode search = MAPPER.createObjectNode();
        search.put("limit", limit(limit == null ? null : IntNode.valueOf(parseInt("limit", limit)), info));
        search.put("offset", start);
        return search;
    }

    /** Fails on a malformed bbox, and replaces one crossing the antimeridian with an equivalent intersects. */
    private static void checkBbox(ObjectNode search) throws PgStacException {
        JsonNode bbox = search.get("bbox");
        if (bbox == null) return;
        boolean numbers = bbox.isArray() && bbox.valueStream().allMatch(JsonNode::isNumber);
        if (!numbers || (bbox.size() != 4 && bbox.size() != 6))
            throw badRequest("Invalid bbox " + bbox + ", must have 4 or 6 numbers");
        // STAC API item search, the split below needs intersects free anyway
        if (search.has("intersects")) throw badRequest("Only one of bbox and intersects can be used");

        // the east longitude starts the second half, 2D or 3D
        int half = bbox.size() / 2;
        double west = bbox.get(0).doubleValue();
        double east = bbox.get(half).doubleValue();
        // west > east crosses the antimeridian (RFC 7946 section 5.2), PgSTAC would read it as east to west instead
        if (west <= east) return;
        double south = bbox.get(1).doubleValue();
        double north = bbox.get(half + 1).doubleValue();
        ObjectNode split = MAPPER.createObjectNode().put("type", "MultiPolygon");
        ArrayNode polygons = split.putArray("coordinates");
        addBox(polygons, west, south, 180, north);
        addBox(polygons, -180, south, east, north);
        // the height range of a 3D bbox is dropped, the split is 2D
        search.remove("bbox");
        search.set("intersects", split);
    }

    /** Adds a GeoJSON polygon, exterior ring counterclockwise as RFC 7946 section 3.1.6 asks. */
    private static void addBox(ArrayNode polygons, double minX, double minY, double maxX, double maxY) {
        ArrayNode ring = polygons.addArray().addArray();
        double[][] corners = {{minX, minY}, {maxX, minY}, {maxX, maxY}, {minX, maxY}, {minX, minY}};
        for (double[] corner : corners) ring.addArray().add(corner[0]).add(corner[1]);
    }

    private static int limit(JsonNode limit, PgStacServiceInfo info) throws PgStacException {
        if (limit == null || limit.isNull()) return Math.min(info.getDefaultPageSize(), info.getMaxFeatures());
        if (!limit.isInt() || limit.intValue() < 1)
            throw badRequest("Invalid limit " + limit + ", must be a positive integer");
        // PgSTAC applies no cap of its own
        return Math.min(limit.intValue(), info.getMaxFeatures());
    }

    private void putFilter(ObjectNode search, ObjectNode body) throws PgStacException {
        JsonNode filter = body.get("filter");
        if (filter == null || filter.isNull()) return;
        String crs = body.path("filter-crs").asString(null);
        // PgSTAC compares geometries as they are, in lon/lat order
        if (crs != null && !isCRS84(crs))
            throw badRequest("Unsupported filter-crs " + crs + ", only " + CRS84 + " is supported");

        // CQL2 JSON goes to PgSTAC as is, avoiding a GeoTools round trip; text languages are converted and checked
        String lang = body.path("filter-lang").asString(null);
        if (filter.isObject() && (lang == null || CQL2_JSON.equals(lang))) {
            search.set("filter", filter);
        } else if (CQL2_JSON.equals(lang)) {
            search.set("filter", readJson("filter", filter.asString()));
        } else {
            search.set("filter", PgStacFilters.toCQL2Json(filterParser.parse(filter.asString(), lang)));
        }
        search.put("filter-lang", CQL2_JSON);
    }

    /** True for any spelling of CRS84 (URI, URN, {@code CRS:84}), never for a latitude-first CRS. */
    private static boolean isCRS84(String crs) {
        try {
            return CRS.equalsIgnoreMetadata(CRS.decode(crs), DefaultGeographicCRS.WGS84);
        } catch (FactoryException e) {
            return false;
        }
    }

    private static void putList(ObjectNode body, String name, String value) {
        if (value == null) return;
        ArrayNode list = body.putArray(name);
        for (String item : value.split(",")) list.add(item.strip());
    }

    private static ArrayNode bbox(String value) throws PgStacException {
        ArrayNode bbox = MAPPER.createArrayNode();
        for (String coordinate : value.split(",")) {
            try {
                bbox.add(Double.parseDouble(coordinate.strip()));
            } catch (NumberFormatException e) {
                throw badRequest("Invalid bbox " + value + ", must be a list of numbers");
            }
        }
        return bbox;
    }

    private static ArrayNode sortBy(String value) {
        ArrayNode sortBy = MAPPER.createArrayNode();
        for (String field : value.split(",")) {
            // an unencoded + decodes to a space, stripped here, see STAC API sort extension
            String name = field.strip();
            boolean descending = name.startsWith("-");
            if (descending || name.startsWith("+")) name = name.substring(1);
            sortBy.addObject().put("field", name).put("direction", descending ? "desc" : "asc");
        }
        return sortBy;
    }

    private static ObjectNode fields(String value) {
        ObjectNode fields = MAPPER.createObjectNode();
        ArrayNode include = fields.putArray("include");
        ArrayNode exclude = fields.putArray("exclude");
        for (String field : value.split(",")) {
            String name = field.strip();
            if (name.startsWith("-")) exclude.add(name.substring(1));
            else include.add(name.startsWith("+") ? name.substring(1) : name);
        }
        return fields;
    }

    private static int parseInt(String name, String value) throws PgStacException {
        try {
            return Integer.parseInt(value.strip());
        } catch (NumberFormatException e) {
            throw badRequest("Invalid " + name + " " + value + ", must be an integer");
        }
    }

    private static JsonNode readJson(String name, String value) throws PgStacException {
        try {
            return MAPPER.readTree(value);
        } catch (JacksonException e) {
            throw new PgStacException(400, "Invalid JSON in " + name + ": " + e.getOriginalMessage(), e);
        }
    }

    private static PgStacException badRequest(String message) {
        return new PgStacException(400, message);
    }
}

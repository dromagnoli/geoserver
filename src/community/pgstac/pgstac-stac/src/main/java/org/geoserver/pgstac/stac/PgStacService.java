/* (c) 2026 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.pgstac.stac;

import static org.geoserver.ogcapi.APIException.INVALID_PARAMETER_VALUE;
import static org.geoserver.ogcapi.ConformanceClass.CQL2_ADVANCED;
import static org.geoserver.ogcapi.ConformanceClass.CQL2_ARRAY;
import static org.geoserver.ogcapi.ConformanceClass.CQL2_BASIC;
import static org.geoserver.ogcapi.ConformanceClass.CQL2_BASIC_SPATIAL;
import static org.geoserver.ogcapi.ConformanceClass.CQL2_JSON;
import static org.geoserver.ogcapi.ConformanceClass.CQL2_SPATIAL;
import static org.geoserver.ogcapi.ConformanceClass.CQL2_TEMPORAL;
import static org.geoserver.ogcapi.ConformanceClass.CQL2_TEXT;
import static org.geoserver.ogcapi.ConformanceClass.FEATURES_FILTER;
import static org.geoserver.ogcapi.ConformanceClass.FILTER;
import static org.geoserver.ogcapi.ConformanceClass.QUERYABLES;
import static org.geoserver.ogcapi.SwaggerJSONAPIMessageConverter.OPEN_API_MEDIA_TYPE_VALUE;
import static org.springframework.http.MediaType.APPLICATION_YAML_VALUE;
import static org.springframework.http.MediaType.TEXT_HTML_VALUE;

import io.swagger.v3.oas.models.OpenAPI;
import java.io.IOException;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.geoserver.config.GeoServer;
import org.geoserver.ogcapi.APIException;
import org.geoserver.ogcapi.APIRequestInfo;
import org.geoserver.ogcapi.APIService;
import org.geoserver.ogcapi.HTMLResponseBody;
import org.geoserver.ows.URLMangler.URLType;
import org.geoserver.ows.util.ResponseUtils;
import org.geoserver.pgstac.GeoServerMetadata;
import org.geoserver.pgstac.PgStacFacade;
import org.geoserver.pgstac.PgStacServiceInfo;
import org.geoserver.pgstac.PgStacServiceInfo.ServiceLevel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * STAC API 1.0 service backed by PgSTAC, named {@code STAC} at {@code ogc/stac/v1} unless the
 * {@code pgstac.service.name} and {@code pgstac.service.path} properties override them.
 */
@APIService(
        service = PgStacService.SERVICE_NAME,
        version = "1.0.0",
        landingPage = PgStacService.SERVICE_PATH,
        serviceClass = PgStacServiceInfo.class)
@RequestMapping(path = PgStacService.SERVICE_PATH)
public class PgStacService {

    // resolved from system properties or environment variables,
    // so it can run next to the OSEO STAC, during the coexistence period
    static final String SERVICE_NAME = "${pgstac.service.name:STAC}";
    static final String SERVICE_PATH = "${pgstac.service.path:ogc/stac/v1}";

    static final String STAC_VERSION = "1.0.0";

    private static final String STAC_CONFORMANCE = "https://api.stacspec.org/v1.0.0/";
    private static final String FEATURES_CONFORMANCE = "http://www.opengis.net/spec/ogcapi-features-1/1.0/conf/";

    /** Advertised on the landing page and /conformance; the CQL2 classes follow the PgSTAC capabilities. */
    static final List<String> CONFORMANCE = List.of(
            STAC_CONFORMANCE + "core",
            STAC_CONFORMANCE + "collections",
            STAC_CONFORMANCE + "ogcapi-features",
            STAC_CONFORMANCE + "item-search",
            STAC_CONFORMANCE + "item-search#filter",
            STAC_CONFORMANCE + "item-search#sort",
            STAC_CONFORMANCE + "item-search#fields",
            STAC_CONFORMANCE + "ogcapi-features#sort",
            STAC_CONFORMANCE + "ogcapi-features#fields",
            FEATURES_CONFORMANCE + "core",
            FEATURES_CONFORMANCE + "geojson",
            FEATURES_CONFORMANCE + "oas30",
            FEATURES_CONFORMANCE + "html",
            QUERYABLES,
            FILTER,
            FEATURES_FILTER,
            CQL2_TEXT,
            CQL2_JSON,
            CQL2_BASIC,
            CQL2_ADVANCED,
            CQL2_BASIC_SPATIAL,
            CQL2_SPATIAL,
            CQL2_TEMPORAL,
            CQL2_ARRAY);

    /** Added to {@link #CONFORMANCE} while the service level allows transactions. */
    static final List<String> TRANSACTION_CONFORMANCE = List.of(
            STAC_CONFORMANCE + "ogcapi-features/extensions/transaction",
            STAC_CONFORMANCE + "collections/extensions/transaction");

    private static final String JSON = "application/json";
    private static final String GEOJSON = "application/geo+json";
    private static final String SCHEMA_JSON = "application/schema+json";

    /** Queryable types without an order, besides {@code geometry}. */
    private static final Set<String> UNSORTABLE_TYPES = Set.of("object", "array");

    /** Precedes the token in the PgSTAC paging links. */
    private static final String TOKEN = "token=";

    /** Link relations the service sets itself, replacing any stored with the collection or item. */
    private static final Set<String> SERVICE_RELS = Set.of("self", "root", "parent", "items", "collection");

    private final GeoServer geoServer;
    private final PgStacFacade facade;
    private final SearchRequestBuilder requestBuilder;
    private final PgStacAPIBuilder apiBuilder = new PgStacAPIBuilder();

    public PgStacService(GeoServer geoServer, PgStacFacade facade, SearchRequestBuilder requestBuilder) {
        this.geoServer = geoServer;
        this.facade = facade;
        this.requestBuilder = requestBuilder;
    }

    /** Returns the global service configuration, used by GeoServer to check the service is enabled. */
    public PgStacServiceInfo getServiceInfo() {
        return geoServer.getService(PgStacServiceInfo.class);
    }

    /** Returns the root STAC catalog. */
    @GetMapping(name = "getLandingPage")
    @ResponseBody
    @HTMLResponseBody(templateName = "landingPage.ftl", fileName = "landingPage.html")
    public ObjectNode landingPage() {
        PgStacServiceInfo info = getServiceInfo();
        ObjectNode catalog = JsonNodeFactory.instance.objectNode();
        catalog.put("type", "Catalog");
        catalog.put("id", info.getName());
        catalog.put("stac_version", STAC_VERSION);
        catalog.put("title", info.getTitle());
        catalog.put("description", info.getAbstract());
        conformsTo().forEach(catalog.putArray("conformsTo")::add);
        ArrayNode links = catalog.putArray("links");
        addLink(links, "self", JSON, "");
        addLink(links, "root", JSON, "");
        addLink(links, "service-desc", OPEN_API_MEDIA_TYPE_VALUE, "openapi");
        addLink(links, "conformance", JSON, "conformance");
        addLink(links, "data", JSON, "collections");
        addLink(links, "search", GEOJSON, "search");
        addLink(links, "http://www.opengis.net/def/rel/ogc/1.0/queryables", SCHEMA_JSON, "queryables");
        addLink(links, "http://www.opengis.net/def/rel/ogc/1.0/sortables", SCHEMA_JSON, "sortables");
        return catalog;
    }

    /** Returns the conformance classes, the same as the landing page {@code conformsTo}. */
    @GetMapping(path = "conformance", name = "getConformanceDeclaration")
    @ResponseBody
    @HTMLResponseBody(templateName = "conformance.ftl", fileName = "conformance.html")
    public ObjectNode conformance() {
        ObjectNode conformance = JsonNodeFactory.instance.objectNode();
        conformsTo().forEach(conformance.putArray("conformsTo")::add);
        return conformance;
    }

    private List<String> conformsTo() {
        if (getServiceInfo().getServiceLevel() != ServiceLevel.TRANSACTIONAL) return CONFORMANCE;
        return Stream.concat(CONFORMANCE.stream(), TRANSACTION_CONFORMANCE.stream())
                .toList();
    }

    /** Returns the OpenAPI definition of this service. */
    @GetMapping(
            path = {"openapi", "openapi.json", "openapi.yaml"},
            name = "getApi",
            produces = {OPEN_API_MEDIA_TYPE_VALUE, APPLICATION_YAML_VALUE, TEXT_HTML_VALUE})
    @ResponseBody
    @HTMLResponseBody(templateName = "api.ftl", fileName = "api.html")
    public OpenAPI api() throws IOException {
        return apiBuilder.build(getServiceInfo());
    }

    /** Returns a page of the collections the user can see, paged by {@code limit} and {@code offset}. */
    @GetMapping(path = "collections", name = "getCollections")
    @ResponseBody
    @HTMLResponseBody(templateName = "collections.ftl", fileName = "collections.html")
    public ObjectNode collections(@RequestParam Map<String, String> params) throws IOException {
        ObjectNode search = requestBuilder.fromCollectionsQuery(params, getServiceInfo());
        ObjectNode page = facade.collectionSearch(search);
        for (JsonNode collection : page.path("collections")) prepareCollection((ObjectNode) collection);

        // PgSTAC pages with a body to merge, turned into plain GET links
        ArrayNode paging = page.withArray("links");
        ArrayNode links = page.putArray("links");
        for (JsonNode link : paging) {
            Map<String, String> kvp = new LinkedHashMap<>();
            kvp.put("limit", search.get("limit").asString());
            kvp.put("offset", link.at("/body/offset").asString());
            String href = url("collections", kvp);
            links.addObject()
                    .put("rel", link.path("rel").asString())
                    .put("type", JSON)
                    .put("href", href);
        }
        addLink(links, "self", JSON, "collections");
        addLink(links, "root", JSON, "");
        return page;
    }

    /** Returns a single collection, 404 when missing or not visible to the user. */
    @GetMapping(path = "collections/{collectionId}", name = "getCollection")
    @ResponseBody
    @HTMLResponseBody(templateName = "collection.ftl", fileName = "collection.html")
    public ObjectNode collection(@PathVariable("collectionId") String collectionId) throws IOException {
        ObjectNode collection = facade.getCollection(collectionId);
        if (collection == null) throw notFound("Collection not found: " + collectionId);
        prepareCollection(collection);
        return collection;
    }

    /** Creates a collection and returns it with a 201 and its location; 409 when the id is taken. */
    @PostMapping(path = "collections", name = "createCollection", produces = JSON)
    public ResponseEntity<ObjectNode> createCollection(@RequestBody ObjectNode collection) throws IOException {
        String id = requireId(collection);
        facade.createCollection(collection);
        return created(collectionPath(id), collection(id));
    }

    /** Replaces a collection and returns the stored one; 404 when missing, 400 when the body has another id. */
    @PutMapping(path = "collections/{collectionId}", name = "replaceCollection", produces = JSON)
    @ResponseBody
    public ObjectNode replaceCollection(
            @PathVariable("collectionId") String collectionId, @RequestBody ObjectNode collection) throws IOException {
        claim(collection, "id", collectionId);
        facade.updateCollection(collection);
        return collection(collectionId);
    }

    /** Deletes a collection with its items; 404 when missing. */
    @DeleteMapping(path = "collections/{collectionId}", name = "deleteCollection")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCollection(@PathVariable("collectionId") String collectionId) throws IOException {
        facade.deleteCollection(collectionId);
    }

    /** Returns a page of the items of a collection, 404 when the collection is missing or not visible. */
    @GetMapping(
            path = "collections/{collectionId}/items",
            name = "getItems",
            produces = {GEOJSON, JSON, TEXT_HTML_VALUE})
    @ResponseBody
    @HTMLResponseBody(templateName = "items.ftl", fileName = "items.html")
    public ObjectNode items(@PathVariable("collectionId") String collectionId, @RequestParam Map<String, String> params)
            throws IOException {
        if (facade.getCollection(collectionId) == null) throw notFound("Collection not found: " + collectionId);
        ObjectNode search = requestBuilder.fromQuery(params, getServiceInfo());
        // the path wins over a collections parameter
        search.putArray("collections").add(collectionId);
        return preparePage(facade.search(search), collectionPath(collectionId) + "/items", params);
    }

    /** Returns a single item, 404 when missing or not visible to the user. */
    @GetMapping(
            path = "collections/{collectionId}/items/{itemId:.+}",
            name = "getItem",
            produces = {GEOJSON, JSON, TEXT_HTML_VALUE})
    @ResponseBody
    @HTMLResponseBody(templateName = "item.ftl", fileName = "item.html")
    public ObjectNode item(@PathVariable("collectionId") String collectionId, @PathVariable("itemId") String itemId)
            throws IOException {
        ObjectNode item = facade.getItem(collectionId, itemId);
        if (item == null) throw notFound("Item not found: " + itemId);
        prepareItem(item);
        return item;
    }

    /**
     * Creates one item in a collection and returns it with a 201 and its location; 404 when the collection is missing,
     * 409 when the id is taken, 400 for an ItemCollection.
     */
    @PostMapping(
            path = "collections/{collectionId}/items",
            name = "createItem",
            produces = {GEOJSON, JSON})
    public ResponseEntity<ObjectNode> createItem(
            @PathVariable("collectionId") String collectionId, @RequestBody ObjectNode item) throws IOException {
        if (item.path("type").asString("").equals("FeatureCollection")) {
            throw badRequest("ItemCollection bodies are not supported, post one item at a time");
        }
        String id = requireId(item);
        claim(item, "collection", collectionId);
        if (facade.getCollection(collectionId) == null) throw notFound("Collection not found: " + collectionId);
        facade.createItem(item);
        return created(itemPath(collectionId, id), item(collectionId, id));
    }

    /** Replaces an item and returns the stored one; 404 when missing, 400 when the body names another item. */
    @PutMapping(
            path = "collections/{collectionId}/items/{itemId:.+}",
            name = "replaceItem",
            produces = {GEOJSON, JSON})
    @ResponseBody
    public ObjectNode replaceItem(
            @PathVariable("collectionId") String collectionId,
            @PathVariable("itemId") String itemId,
            @RequestBody ObjectNode item)
            throws IOException {
        claim(item, "id", itemId);
        claim(item, "collection", collectionId);
        facade.updateItem(item);
        return item(collectionId, itemId);
    }

    /** Deletes an item; 404 when missing. */
    @DeleteMapping(path = "collections/{collectionId}/items/{itemId:.+}", name = "deleteItem")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteItem(@PathVariable("collectionId") String collectionId, @PathVariable("itemId") String itemId)
            throws IOException {
        facade.deleteItem(collectionId, itemId);
    }

    private static String requireId(ObjectNode body) {
        String id = body.path("id").asString("");
        if (id.isEmpty()) throw badRequest("The body has no id");
        return id;
    }

    /** Fills {@code field} with the path value, rejecting a body that states another one (STAC transaction, PUT). */
    private static void claim(ObjectNode body, String field, String value) {
        String stated = body.path(field).asString(value);
        if (!stated.equals(value)) throw badRequest("The body " + field + " " + stated + " does not match the path");
        body.put(field, value);
    }

    private static ResponseEntity<ObjectNode> created(String path, ObjectNode body) {
        return ResponseEntity.created(URI.create(url(path, null))).body(body);
    }

    /** Searches the items of all visible collections with the GET parameters of STAC item search. */
    @GetMapping(
            path = "search",
            name = "searchGet",
            produces = {GEOJSON, JSON, TEXT_HTML_VALUE})
    @ResponseBody
    @HTMLResponseBody(templateName = "items.ftl", fileName = "items.html")
    public ObjectNode search(@RequestParam Map<String, String> params) throws IOException {
        ObjectNode search = requestBuilder.fromQuery(params, getServiceInfo());
        return preparePage(facade.search(search), "search", params);
    }

    /** Searches the items of all visible collections with a STAC item search POST body. */
    @PostMapping(
            path = "search",
            name = "searchPost",
            produces = {GEOJSON, JSON})
    @ResponseBody
    public ObjectNode searchPost(@RequestBody ObjectNode body) throws IOException {
        ObjectNode search = requestBuilder.fromBody(body, getServiceInfo());
        return preparePage(facade.search(search), "search", null);
    }

    /**
     * Prepares the items of a search page and rebuilds its paging links, as the PgSTAC ones carry the token alone.
     *
     * @param params the GET parameters to repeat in the paging links, {@code null} for a POST search
     */
    private static ObjectNode preparePage(ObjectNode page, String path, Map<String, String> params) {
        for (JsonNode item : page.path("features")) prepareItem((ObjectNode) item);
        ArrayNode pgstacLinks = page.withArray("links");
        ArrayNode links = page.putArray("links");
        for (JsonNode link : pgstacLinks) {
            String rel = link.path("rel").asString("");
            if (!rel.equals("next") && !rel.equals("prev")) continue;
            String href = link.path("href").asString();
            links.add(pagingLink(rel, path, params, href.substring(href.indexOf(TOKEN) + TOKEN.length())));
        }
        links.addObject().put("rel", "self").put("type", GEOJSON).put("href", url(path, params));
        addLink(links, "root", JSON, "");
        return page;
    }

    /** A GET link repeating the request parameters, or a POST one merging the token into the request body. */
    private static ObjectNode pagingLink(String rel, String path, Map<String, String> params, String token) {
        ObjectNode link = JsonNodeFactory.instance.objectNode().put("rel", rel).put("type", GEOJSON);
        if (params == null) {
            // see the STAC API item search pagination, POST links
            link.put("href", url(path, null)).put("method", "POST").put("merge", true);
            link.putObject("body").put("token", token);
            return link;
        }
        Map<String, String> kvp = new LinkedHashMap<>(params);
        kvp.put("token", token);
        return link.put("href", url(path, kvp));
    }

    /** Returns the JSON schema of the properties usable in filters across all collections, 404 when there are none. */
    @GetMapping(
            path = "queryables",
            name = "getQueryables",
            produces = {SCHEMA_JSON, JSON, TEXT_HTML_VALUE})
    @ResponseBody
    @HTMLResponseBody(templateName = "schema.ftl", fileName = "schema.html")
    public ObjectNode queryables() throws IOException {
        // PgSTAC returns null on a database without collections
        return withSchemaId(facade.getQueryables(null), "queryables", "No collections available");
    }

    /** Returns the JSON schema of the properties usable in filters on a collection, 404 when not visible. */
    @GetMapping(
            path = "collections/{collectionId}/queryables",
            name = "getCollectionQueryables",
            produces = {SCHEMA_JSON, JSON, TEXT_HTML_VALUE})
    @ResponseBody
    @HTMLResponseBody(templateName = "schema.ftl", fileName = "schema.html")
    public ObjectNode collectionQueryables(@PathVariable("collectionId") String collectionId) throws IOException {
        String path = collectionPath(collectionId) + "/queryables";
        return withSchemaId(facade.getQueryables(collectionId), path, "Collection not found: " + collectionId);
    }

    private static ObjectNode withSchemaId(ObjectNode queryables, String path, String notFound) {
        if (queryables == null) throw notFound(notFound);
        // PgSTAC leaves $id empty, JSON Schema wants the schema location
        queryables.put("$id", url(path, null));
        return queryables;
    }

    /** Returns the JSON schema of the properties usable in sortby across all collections, 404 when there are none. */
    @GetMapping(
            path = "sortables",
            name = "getSortables",
            produces = {SCHEMA_JSON, JSON, TEXT_HTML_VALUE})
    @ResponseBody
    @HTMLResponseBody(templateName = "schema.ftl", fileName = "schema.html")
    public ObjectNode sortables() throws IOException {
        return toSortables(queryables(), "sortables");
    }

    /** Returns the JSON schema of the properties usable in sortby on a collection, 404 when not visible. */
    @GetMapping(
            path = "collections/{collectionId}/sortables",
            name = "getCollectionSortables",
            produces = {SCHEMA_JSON, JSON, TEXT_HTML_VALUE})
    @ResponseBody
    @HTMLResponseBody(templateName = "schema.ftl", fileName = "schema.html")
    public ObjectNode collectionSortables(@PathVariable("collectionId") String collectionId) throws IOException {
        return toSortables(collectionQueryables(collectionId), collectionPath(collectionId) + "/sortables");
    }

    /**
     * Keeps the queryables with an order. PgSTAC sorts any other property as text and without an index, so it is
     * accepted in sortby but not advertised.
     */
    private static ObjectNode toSortables(ObjectNode queryables, String path) {
        ObjectNode properties = queryables.withObject("properties");
        properties.remove("geometry");
        List<String> unsortable = properties
                .propertyStream()
                .filter(p -> UNSORTABLE_TYPES.contains(p.getValue().path("type").asString("")))
                .map(Map.Entry::getKey)
                .toList();
        properties.remove(unsortable);
        // additionalProperties tells which other names filters accept, it does not apply to sorting
        queryables.remove("additionalProperties");
        return queryables.put("title", "Sortables").put("$id", url(path, null));
    }

    /** Removes the GeoServer-only metadata and sets the links pointing back at this service. */
    private static void prepareCollection(ObjectNode collection) {
        ArrayNode links = prepare(collection);
        String path = collectionPath(collection.path("id").asString());
        addLink(links, "self", JSON, path);
        addLink(links, "root", JSON, "");
        addLink(links, "parent", JSON, "");
        addLink(links, "items", GEOJSON, path + "/items");
    }

    /** Removes the GeoServer-only metadata and sets the links pointing back at this service. */
    private static void prepareItem(ObjectNode item) {
        ArrayNode links = prepare(item);
        String collectionId = item.path("collection").asString();
        String collection = collectionPath(collectionId);
        addLink(links, "self", GEOJSON, itemPath(collectionId, item.path("id").asString()));
        addLink(links, "parent", JSON, collection);
        addLink(links, "collection", JSON, collection);
        addLink(links, "root", JSON, "");
    }

    /** Strips a collection or an item, returning its stored links without the ones this service sets. */
    private static ArrayNode prepare(ObjectNode node) {
        GeoServerMetadata.strip(node);
        ArrayNode stored = node.withArray("links");
        ArrayNode links = node.putArray("links");
        for (JsonNode link : stored) {
            if (!SERVICE_RELS.contains(link.path("rel").asString(""))) links.add(link);
        }
        return links;
    }

    private static String collectionPath(String collectionId) {
        return "collections/" + ResponseUtils.urlEncode(collectionId);
    }

    private static String itemPath(String collectionId, String itemId) {
        return collectionPath(collectionId) + "/items/" + ResponseUtils.urlEncode(itemId);
    }

    private static void addLink(ArrayNode links, String rel, String type, String path) {
        links.addObject().put("rel", rel).put("type", type).put("href", url(path, null));
    }

    /** Absolute URL of a resource of this service, {@code path} relative to the landing page. */
    private static String url(String path, Map<String, String> kvp) {
        APIRequestInfo request = APIRequestInfo.get();
        String landingPage = request.getServiceLandingPage();
        String resource = path.isEmpty() ? landingPage : landingPage + "/" + path;
        return ResponseUtils.buildURL(request.getBaseURL(), resource, kvp, URLType.SERVICE);
    }

    private static APIException notFound(String message) {
        return new APIException(APIException.NOT_FOUND, message, HttpStatus.NOT_FOUND);
    }

    private static APIException badRequest(String message) {
        return new APIException(INVALID_PARAMETER_VALUE, message, HttpStatus.BAD_REQUEST);
    }
}

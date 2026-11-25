/* (c) 2026 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.pgstac.stac;

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

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.geoserver.config.GeoServer;
import org.geoserver.ogcapi.APIException;
import org.geoserver.ogcapi.APIRequestInfo;
import org.geoserver.ogcapi.APIService;
import org.geoserver.ows.URLMangler.URLType;
import org.geoserver.ows.util.ResponseUtils;
import org.geoserver.pgstac.GeoServerMetadata;
import org.geoserver.pgstac.PgStacFacade;
import org.geoserver.pgstac.PgStacServiceInfo;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
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
            FEATURES_CONFORMANCE + "core",
            FEATURES_CONFORMANCE + "geojson",
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

    private static final String JSON = "application/json";
    private static final String GEOJSON = "application/geo+json";
    private static final String SCHEMA_JSON = "application/schema+json";

    /** Link relations the service sets itself, replacing any stored with the collection. */
    private static final Set<String> SERVICE_RELS = Set.of("self", "root", "parent", "items");

    private final GeoServer geoServer;
    private final PgStacFacade facade;
    private final SearchRequestBuilder requestBuilder;

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
    public ObjectNode landingPage() {
        PgStacServiceInfo info = getServiceInfo();
        ObjectNode catalog = JsonNodeFactory.instance.objectNode();
        catalog.put("type", "Catalog");
        catalog.put("id", info.getName());
        catalog.put("stac_version", STAC_VERSION);
        catalog.put("title", info.getTitle());
        catalog.put("description", info.getAbstract());
        CONFORMANCE.forEach(catalog.putArray("conformsTo")::add);
        ArrayNode links = catalog.putArray("links");
        addLink(links, "self", JSON, "");
        addLink(links, "root", JSON, "");
        addLink(links, "conformance", JSON, "conformance");
        addLink(links, "data", JSON, "collections");
        addLink(links, "search", GEOJSON, "search");
        addLink(links, "http://www.opengis.net/def/rel/ogc/1.0/queryables", SCHEMA_JSON, "queryables");
        return catalog;
    }

    /** Returns the conformance classes, the same as the landing page {@code conformsTo}. */
    @GetMapping(path = "conformance", name = "getConformanceDeclaration")
    @ResponseBody
    public ObjectNode conformance() {
        ObjectNode conformance = JsonNodeFactory.instance.objectNode();
        CONFORMANCE.forEach(conformance.putArray("conformsTo")::add);
        return conformance;
    }

    /** Returns a page of the collections the user can see, paged by {@code limit} and {@code offset}. */
    @GetMapping(path = "collections", name = "getCollections")
    @ResponseBody
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
    public ObjectNode collection(@PathVariable("collectionId") String collectionId) throws IOException {
        ObjectNode collection = facade.getCollection(collectionId);
        if (collection == null) throw notFound("Collection not found: " + collectionId);
        prepareCollection(collection);
        return collection;
    }

    /** Returns the JSON schema of the properties usable in filters across all collections, 404 when there are none. */
    @GetMapping(
            path = "queryables",
            name = "getQueryables",
            produces = {SCHEMA_JSON, JSON})
    @ResponseBody
    public ObjectNode queryables() throws IOException {
        // PgSTAC returns null on a database without collections
        return withSchemaId(facade.getQueryables(null), "queryables", "No collections available");
    }

    /** Returns the JSON schema of the properties usable in filters on a collection, 404 when not visible. */
    @GetMapping(
            path = "collections/{collectionId}/queryables",
            name = "getCollectionQueryables",
            produces = {SCHEMA_JSON, JSON})
    @ResponseBody
    public ObjectNode collectionQueryables(@PathVariable("collectionId") String collectionId) throws IOException {
        String path = "collections/" + ResponseUtils.urlEncode(collectionId) + "/queryables";
        return withSchemaId(facade.getQueryables(collectionId), path, "Collection not found: " + collectionId);
    }

    private static ObjectNode withSchemaId(ObjectNode queryables, String path, String notFound) {
        if (queryables == null) throw notFound(notFound);
        // PgSTAC leaves $id empty, JSON Schema wants the schema location
        queryables.put("$id", url(path, null));
        return queryables;
    }

    /** Removes the GeoServer-only metadata and sets the links pointing back at this service. */
    private static void prepareCollection(ObjectNode collection) {
        GeoServerMetadata.strip(collection);
        String path =
                "collections/" + ResponseUtils.urlEncode(collection.path("id").asString());
        ArrayNode stored = collection.withArray("links");
        ArrayNode links = collection.putArray("links");
        for (JsonNode link : stored) {
            if (!SERVICE_RELS.contains(link.path("rel").asString(""))) links.add(link);
        }
        addLink(links, "self", JSON, path);
        addLink(links, "root", JSON, "");
        addLink(links, "parent", JSON, "");
        addLink(links, "items", GEOJSON, path + "/items");
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
}

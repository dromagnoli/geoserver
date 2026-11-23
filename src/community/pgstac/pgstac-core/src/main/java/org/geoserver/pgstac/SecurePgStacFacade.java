/* (c) 2026 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.pgstac;

import static java.util.stream.Collectors.toSet;
import static org.geoserver.pgstac.PgStacFilters.and;
import static org.geoserver.pgstac.PgStacFilters.toCQL2Json;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.geoserver.catalog.WorkspaceInfo;
import org.geoserver.config.GeoServer;
import org.geoserver.ows.LocalWorkspace;
import org.geoserver.pgstac.PgStacServiceInfo.ServiceLevel;
import org.geoserver.security.GeoServerSecurityManager;
import org.geotools.api.filter.Filter;
import org.geotools.api.filter.FilterFactory;
import org.geotools.factory.CommonFactoryFinder;
import org.geotools.filter.text.cql2.CQLException;
import org.geotools.filter.text.ecql.ECQL;
import org.springframework.context.annotation.Primary;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * {@link PgStacFacade} hiding what the {@link AccessRule}s deny to the current user (administrators see everything) and
 * the collections outside the current workspace, and allowing writes to administrators only.
 */
@Primary
@Component
public class SecurePgStacFacade implements PgStacFacade {

    private static final FilterFactory FF = CommonFactoryFinder.getFilterFactory();

    private static final JsonMapper MAPPER = new JsonMapper();

    private final PgStacFacade delegate;
    private final GeoServer geoServer;
    private final GeoServerSecurityManager securityManager;

    public SecurePgStacFacade(
            DefaultPgStacFacade delegate, GeoServer geoServer, GeoServerSecurityManager securityManager) {
        this.delegate = delegate;
        this.geoServer = geoServer;
        this.securityManager = securityManager;
    }

    @Override
    public ObjectNode search(ObjectNode request) throws IOException {
        Set<String> visible = visibleCollections();
        // an empty collections array makes PgSTAC return an empty page
        if (visible != null) request.set("collections", visibleRequested(request, visible));
        and(request, denied(service().getItemAccessRules(), true));
        return delegate.search(request);
    }

    /** Returns the ids of the collections the current user can see, or {@code null} when nothing is hidden. */
    private Set<String> visibleCollections() throws IOException {
        // collection_search returns 10 collections unless told otherwise; PgSTAC rejects the largest int as limit,
        // since search() reads one row past it
        ObjectNode request = MAPPER.createObjectNode().put("limit", Integer.MAX_VALUE - 1);
        request.putObject("fields").putArray("include").add("id");
        if (!restrictCollections(request)) return null;

        Set<String> ids = new HashSet<>();
        for (JsonNode collection : delegate.collectionSearch(request).path("collections")) {
            ids.add(collection.path("id").asString());
        }
        return ids;
    }

    private static ArrayNode visibleRequested(ObjectNode request, Set<String> visible) {
        ArrayNode result = MAPPER.createArrayNode();
        JsonNode requested = request.get("collections");
        if (requested == null) {
            visible.forEach(result::add);
            return result;
        }
        for (JsonNode id : requested) {
            if (visible.contains(id.asString())) result.add(id);
        }
        return result;
    }

    /** ANDs the denied collection rules and the workspace filter into the request, false if neither applies. */
    private boolean restrictCollections(ObjectNode request) throws IOException {
        ObjectNode rules = denied(service().getCollectionAccessRules(), false);
        ObjectNode workspace = workspaceFilter();
        and(request, rules);
        and(request, workspace);
        return rules != null || workspace != null;
    }

    /**
     * Returns the CQL2 JSON excluding what the rules not granted to the current user match, or {@code null} when every
     * rule is granted; item rules are {@code scoped} to their collection.
     */
    private ObjectNode denied(List<AccessRule> rules, boolean scoped) throws PgStacException {
        if (securityManager.checkAuthenticationForAdminRole()) return null;
        Set<String> roles = userRoles();
        List<Filter> denials = new ArrayList<>();
        for (AccessRule rule : rules) {
            if (!Collections.disjoint(rule.getRoles(), roles)) continue;
            Filter filter = parse(rule);
            if (scoped) filter = FF.and(FF.equals(FF.property("collection"), FF.literal(rule.getCollection())), filter);
            denials.add(FF.not(filter));
        }
        if (denials.isEmpty()) return null;
        return toCQL2Json(denials.size() == 1 ? denials.get(0) : FF.and(denials));
    }

    private static Set<String> userRoles() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return Set.of();
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(toSet());
    }

    private static Filter parse(AccessRule rule) throws PgStacException {
        try {
            return ECQL.toFilter(rule.getCqlFilter());
        } catch (CQLException e) {
            throw new PgStacException(500, "Invalid access rule filter: " + rule.getCqlFilter(), e);
        }
    }

    /** Matches collections listing the current workspace, or listing none (global), {@code null} outside workspaces. */
    private static ObjectNode workspaceFilter() {
        WorkspaceInfo workspace = LocalWorkspace.get();
        if (workspace == null) return null;
        // collection_search sees the collection JSON as item properties, so the property resolves to the top-level
        // _geoserver entry; a_contains needs an array literal
        String filter =
                """
                {"op": "or", "args": [
                  {"op": "a_contains", "args": [{"property": "_geoserver.workspaces"}, [%s]]},
                  {"op": "isNull", "args": [{"property": "_geoserver.workspaces"}]}]}
                """;
        return (ObjectNode) MAPPER.readTree(filter.formatted(MAPPER.writeValueAsString(workspace.getName())));
    }

    @Override
    public ObjectNode getCollection(String id) throws IOException {
        return isVisible(id) ? delegate.getCollection(id) : null;
    }

    private boolean isVisible(String collectionId) throws IOException {
        ObjectNode request = MAPPER.createObjectNode();
        request.putArray("ids").add(collectionId);
        if (!restrictCollections(request)) return true;
        return !delegate.collectionSearch(request).path("collections").isEmpty();
    }

    @Override
    public ObjectNode collectionSearch(ObjectNode request) throws IOException {
        restrictCollections(request);
        return delegate.collectionSearch(request);
    }

    @Override
    public ObjectNode getItem(String collectionId, String itemId) throws IOException {
        if (!isVisible(collectionId)) return null;
        ObjectNode rules = denied(service().getItemAccessRules(), true);
        if (rules != null) {
            ObjectNode request = MAPPER.createObjectNode().put("limit", 1);
            request.putArray("collections").add(collectionId);
            request.putArray("ids").add(itemId);
            and(request, rules);
            if (delegate.search(request).path("features").isEmpty()) return null;
        }
        return delegate.getItem(collectionId, itemId);
    }

    @Override
    public ObjectNode getQueryables(String collectionId) throws IOException {
        // a null collection asks for the queryables shared by all collections
        return collectionId == null || isVisible(collectionId) ? delegate.getQueryables(collectionId) : null;
    }

    @Override
    public String getVersion() throws IOException {
        return delegate.getVersion();
    }

    /** {@inheritDoc} Not filtered: the operator metadata drives the published layers, whoever requests them. */
    @Override
    public ObjectNode getCollectionPrivate(String id) throws IOException {
        return delegate.getCollectionPrivate(id);
    }

    @Override
    public void setCollectionPrivate(String id, ObjectNode metadata) throws IOException {
        checkAdmin();
        delegate.setCollectionPrivate(id, metadata);
    }

    @Override
    public void createCollection(ObjectNode collection) throws IOException {
        checkTransactional();
        delegate.createCollection(collection);
    }

    @Override
    public void updateCollection(ObjectNode collection) throws IOException {
        checkTransactional();
        delegate.updateCollection(collection);
    }

    @Override
    public void deleteCollection(String id) throws IOException {
        checkTransactional();
        delegate.deleteCollection(id);
    }

    @Override
    public void createItem(ObjectNode item) throws IOException {
        checkTransactional();
        delegate.createItem(item);
    }

    @Override
    public void updateItem(ObjectNode item) throws IOException {
        checkTransactional();
        delegate.updateItem(item);
    }

    @Override
    public void deleteItem(String collectionId, String itemId) throws IOException {
        checkTransactional();
        delegate.deleteItem(collectionId, itemId);
    }

    private void checkTransactional() throws PgStacException {
        if (service().getServiceLevel() != ServiceLevel.TRANSACTIONAL) {
            throw new PgStacException(403, "STAC transactions are disabled");
        }
        checkAdmin();
    }

    private void checkAdmin() throws PgStacException {
        if (!securityManager.checkAuthenticationForAdminRole()) {
            throw new PgStacException(403, "Administrator rights required");
        }
    }

    private PgStacServiceInfo service() {
        return geoServer.getService(PgStacServiceInfo.class);
    }
}

/* (c) 2026 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.pgstac;

import java.io.IOException;
import java.io.Serializable;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import org.geoserver.config.GeoServer;
import org.geotools.jdbc.JDBCDataStore;
import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * JDBC-only {@link PgStacFacade}: every method opens a connection, calls exactly one {@code pgstac.*} function (or, for
 * {@code collections.private} and the queryable rows, a direct SQL statement), and translates PostgreSQL errors to HTTP
 * status codes.
 *
 * <p>Each call borrows a connection from the pool and prepares a fresh {@link PreparedStatement};
 */
@Component
public class DefaultPgStacFacade implements PgStacFacade {

    /** Supported PgSTAC major.minor; minors can break the SQL API too, so each one needs a review before enabling. */
    private static final int SUPPORTED_MAJOR = 0;

    private static final int SUPPORTED_MINOR = 10;

    private final PgStacConnectionManager connectionManager;
    private final GeoServer geoServer;
    private final JsonMapper mapper = new JsonMapper();

    private volatile String cachedVersion;

    public DefaultPgStacFacade(PgStacConnectionManager connectionManager, GeoServer geoServer) {
        this.connectionManager = connectionManager;
        this.geoServer = geoServer;
    }

    @Override
    public String getVersion() throws IOException {
        if (cachedVersion == null) {
            String version = queryText("SELECT pgstac.get_version()");
            if (!isSupportedVersion(version)) throw unsupportedVersion(version);
            cachedVersion = version;
        }
        return cachedVersion;
    }

    /**
     * Connects with {@code params} on a pool of its own, closed before returning, and returns the PgSTAC version found
     * there; fails when the database cannot be reached, has no PgSTAC schema or runs an unsupported version.
     */
    public String testConnection(Map<String, Serializable> params) throws IOException {
        JDBCDataStore store = connectionManager.createStore(params);
        try (Connection c = store.getDataSource().getConnection();
                Statement st = c.createStatement();
                ResultSet rs = st.executeQuery("SELECT pgstac.get_version()")) {
            rs.next();
            String version = rs.getString(1);
            if (!isSupportedVersion(version)) throw unsupportedVersion(version);
            return version;
        } catch (SQLException e) {
            throw translate(e);
        } finally {
            store.dispose();
        }
    }

    private static PgStacException unsupportedVersion(String version) {
        String next = SUPPORTED_MAJOR + "." + (SUPPORTED_MINOR + 1);
        String expected = ">=" + SUPPORTED_MAJOR + "." + SUPPORTED_MINOR + ", <" + next;
        return new PgStacException(500, "Unsupported PgSTAC version " + version + ", expected " + expected);
    }

    static boolean isSupportedVersion(String version) {
        if (version == null) return false;
        String[] parts = version.split("\\.");
        if (parts.length < 2) return false;
        try {
            return Integer.parseInt(parts[0]) == SUPPORTED_MAJOR && Integer.parseInt(parts[1]) == SUPPORTED_MINOR;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    @Override
    public ObjectNode search(ObjectNode request) throws IOException {
        getVersion();
        boolean apiHydrate = geoServer.getService(PgStacServiceInfo.class).isUseApiHydrate();
        // don't delegate on PgStac hydrate.
        if (apiHydrate) request.withObjectProperty("conf").put("nohydrate", true);
        ObjectNode page = callForObject("SELECT pgstac.search(?::jsonb)", mapper.writeValueAsString(request));
        // without hydration PgSTAC also skips the fields projection, hydrate() applies it
        if (apiHydrate && page != null) PgStacHydration.hydrate(page, request.get("fields"));
        return page;
    }

    @Override
    public ObjectNode getCollection(String id) throws IOException {
        getVersion();
        return callForObject("SELECT pgstac.get_collection(?)", id);
    }

    @Override
    public ObjectNode collectionSearch(ObjectNode request) throws IOException {
        getVersion();
        return callForObject("SELECT pgstac.collection_search(?::jsonb)", mapper.writeValueAsString(request));
    }

    @Override
    public ObjectNode getItem(String collectionId, String itemId) throws IOException {
        getVersion();
        return callForObject("SELECT pgstac.get_item(?, ?)", itemId, collectionId);
    }

    @Override
    public ObjectNode getQueryables(String collectionId) throws IOException {
        getVersion();
        return callForObject("SELECT pgstac.get_queryables(?)", collectionId);
    }

    @Override
    public ObjectNode getQueryableRows(String name) throws IOException {
        getVersion();
        // a table read, as no PgSTAC function returns the rows: get_queryables answers the STAC queryables document,
        // one schema per property name, without the collections each row applies to or its index settings
        String sql =
                """
                SELECT jsonb_build_object('queryables', coalesce(jsonb_agg(to_jsonb(q) - 'id' ORDER BY name, id), '[]'))
                FROM pgstac.queryables q
                WHERE ?::text IS NULL OR name = pgstac.strip_properties_prefix(?)""";
        return callForObject(sql, name, name);
    }

    @Override
    public void upsertQueryable(String name, ObjectNode queryable) throws IOException {
        getVersion();
        // a JSON null member reads as a missing one
        String sql =
                """
                SELECT pgstac.upsert_queryable(?, nullif(q->'definition', 'null'), q->>'property_wrapper',
                    q->>'property_index_type', pgstac.to_text_array(nullif(q->'collection_ids', 'null')),
                    pgstac.to_text_array(nullif(q->'property_path', 'null')))
                FROM (SELECT ?::jsonb AS q) body""";
        call(sql, name, mapper.writeValueAsString(queryable));
    }

    @Override
    public void deleteQueryable(String name, List<String> collectionIds) throws IOException {
        getVersion();
        String[] ids = collectionIds == null ? null : collectionIds.toArray(String[]::new);
        String scope = ids == null || ids.length == 0 ? "all collections" : "collections " + String.join(", ", ids);
        String notFound = "Queryable " + name + " for " + scope + " not found";
        write(404, notFound, "SELECT pgstac.delete_queryable(?, ?::text[])", name, ids);
    }

    /**
     * Reads the {@code private jsonb} column of {@code pgstac.collections} (one column, not a separate table) - the
     * operator-metadata store no PgSTAC function exposes, see {@link PgStacFacade}.
     */
    @Override
    public ObjectNode getCollectionPrivate(String id) throws IOException {
        getVersion();
        return callForObject("SELECT private FROM pgstac.collections WHERE id = ?", id);
    }

    @Override
    public void setCollectionPrivate(String id, ObjectNode metadata) throws IOException {
        getVersion();
        call("UPDATE pgstac.collections SET private = ?::jsonb WHERE id = ?", mapper.writeValueAsString(metadata), id);
    }

    @Override
    public void createCollection(ObjectNode collection) throws IOException {
        getVersion();
        String json = mapper.writeValueAsString(collection);
        String id = collection.path("id").asString();
        write(409, "Collection " + id + " already exists", "SELECT pgstac.create_collection(?::jsonb)", json);
    }

    @Override
    public void updateCollection(ObjectNode collection) throws IOException {
        getVersion();
        // _geoserver never reaches API clients, so a GET-then-PUT round trip would otherwise wipe it
        String id = collection.path("id").asString();
        GeoServerMetadata.graft(getCollection(id), collection);
        String json = mapper.writeValueAsString(collection);
        write(404, "Collection " + id + " not found", "SELECT pgstac.update_collection(?::jsonb)", json);
    }

    @Override
    public void deleteCollection(String id) throws IOException {
        getVersion();
        write(404, "Collection " + id + " not found", "SELECT pgstac.delete_collection(?)", id);
    }

    @Override
    public void createItem(ObjectNode item) throws IOException {
        getVersion();
        String json = mapper.writeValueAsString(item);
        write(409, itemName(item) + " already exists", "SELECT pgstac.create_item(?::jsonb)", json);
    }

    @Override
    public void updateItem(ObjectNode item) throws IOException {
        getVersion();
        GeoServerMetadata.graft(
                getItem(item.path("collection").asString(), item.path("id").asString()), item);
        String json = mapper.writeValueAsString(item);
        write(404, itemName(item) + " not found", "SELECT pgstac.update_item(?::jsonb)", json);
    }

    @Override
    public void deleteItem(String collectionId, String itemId) throws IOException {
        getVersion();
        String notFound = itemName(collectionId, itemId) + " not found";
        write(404, notFound, "SELECT pgstac.delete_item(?, ?)", itemId, collectionId);
    }

    private static String itemName(ObjectNode item) {
        return itemName(item.path("collection").asString(), item.path("id").asString());
    }

    private static String itemName(String collectionId, String itemId) {
        return "Item " + itemId + " in collection " + collectionId;
    }

    /**
     * Runs a write, replacing the PgSTAC message of the expected failure with one naming the object: PostgreSQL reports
     * a missing row as "query returned no rows" and a duplicate with the constraint name.
     */
    private void write(int status, String message, String sql, Object... params) throws IOException {
        try {
            call(sql, params);
        } catch (PgStacException e) {
            if (e.getHttpStatus() != status) throw e;
            throw new PgStacException(status, message, e);
        }
    }

    private ObjectNode callForObject(String sql, Object... params) throws IOException {
        try (Connection c = connect();
                PreparedStatement ps = c.prepareStatement(sql)) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                String json = rs.getString(1);
                return json == null ? null : (ObjectNode) mapper.readTree(json);
            }
        } catch (SQLException e) {
            throw translate(e);
        }
    }

    private String queryText(String sql, Object... params) throws IOException {
        try (Connection c = connect();
                PreparedStatement ps = c.prepareStatement(sql)) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        } catch (SQLException e) {
            throw translate(e);
        }
    }

    private void call(String sql, Object... params) throws IOException {
        try (Connection c = connect();
                PreparedStatement ps = c.prepareStatement(sql)) {
            bind(ps, params);
            ps.execute();
        } catch (SQLException e) {
            throw translate(e);
        }
    }

    /** Borrows a pooled connection whose search_path lets PgSTAC functions resolve their own tables. */
    private Connection connect() throws IOException, SQLException {
        Connection c = connectionManager.getDataSource().getConnection();
        // PgSTAC functions reference their tables unqualified (search() fails with "relation searches does not
        // exist" otherwise), and the database/role default cannot be relied upon
        try (Statement st = c.createStatement()) {
            st.execute("SET search_path TO pgstac, public");
            return c;
        } catch (SQLException e) {
            c.close();
            throw e;
        }
    }

    private static void bind(PreparedStatement ps, Object... params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            ps.setObject(i + 1, params[i]);
        }
    }

    /** Maps PostgreSQL/PgSTAC errors to HTTP status, per the facade error translation table. */
    static PgStacException translate(SQLException e) {
        String sqlState = e.getSQLState() == null ? "" : e.getSQLState();
        // the server message alone: getMessage() appends the PL/pgSQL context, function names and line numbers
        ServerErrorMessage server = e instanceof PSQLException pe ? pe.getServerErrorMessage() : null;
        String message = server == null ? e.getMessage() : server.getMessage();
        if (sqlState.equals("23505")) {
            return new PgStacException(409, "Conflict: " + message, e);
        }
        // the other integrity violations, e.g. 23503 missing collection, 23514 queryable rule
        if (sqlState.startsWith("23")) {
            return new PgStacException(400, "Bad request: " + message, e);
        }
        // PgSTAC rejects bad input (filters, limit, sortby, datetime) with a plain RAISE (P0001) or a class 22 data
        // exception, e.g. 22023 invalid_parameter_value, 22003 integer out of range
        if (sqlState.equals("P0001") || sqlState.startsWith("22")) {
            return new PgStacException(400, message, e);
        }
        // raised as no_data_found, e.g. "Collection x does not exist"
        if (sqlState.equals("P0002")) {
            return new PgStacException(404, message, e);
        }
        return new PgStacException(500, "PgSTAC call failed: " + message, e);
    }
}

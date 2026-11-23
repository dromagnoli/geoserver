/* (c) 2026 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.pgstac;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.geoserver.config.GeoServer;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * JDBC-only {@link PgStacFacade}: every method opens a connection, calls exactly one {@code pgstac.*} function (or, for
 * {@code collections.private}, a direct SQL statement), and translates PostgreSQL errors to HTTP status codes.
 *
 * <p>Each call borrows a connection from the pool (cheap - no new TCP handshake) and prepares a fresh
 * {@link PreparedStatement}; no statement caching is done here since pgjdbc already promotes repeated SQL text to a
 * server-side prepared plan per physical connection ({@code prepareThreshold}).
 */
@Component
public class DefaultPgStacFacade implements PgStacFacade {

    /**
     * Supported PgSTAC major.minor; minors break the SQL API (0.10 did), so each one needs a review before enabling.
     */
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
            if (!isSupportedVersion(version)) {
                throw new PgStacException(
                        500,
                        "Unsupported PgSTAC version "
                                + version
                                + ", expected >="
                                + SUPPORTED_MAJOR
                                + "."
                                + SUPPORTED_MINOR
                                + ", <"
                                + SUPPORTED_MAJOR
                                + "."
                                + (SUPPORTED_MINOR + 1));
            }
            cachedVersion = version;
        }
        return cachedVersion;
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
        call("SELECT pgstac.create_collection(?::jsonb)", mapper.writeValueAsString(collection));
    }

    @Override
    public void updateCollection(ObjectNode collection) throws IOException {
        getVersion();
        // _geoserver never reaches API clients, so a GET-then-PUT round trip would otherwise wipe it
        GeoServerMetadata.graft(getCollection(collection.path("id").asString()), collection);
        call("SELECT pgstac.update_collection(?::jsonb)", mapper.writeValueAsString(collection));
    }

    @Override
    public void deleteCollection(String id) throws IOException {
        getVersion();
        call("SELECT pgstac.delete_collection(?)", id);
    }

    @Override
    public void createItem(ObjectNode item) throws IOException {
        getVersion();
        call("SELECT pgstac.create_item(?::jsonb)", mapper.writeValueAsString(item));
    }

    @Override
    public void updateItem(ObjectNode item) throws IOException {
        getVersion();
        GeoServerMetadata.graft(
                getItem(item.path("collection").asString(), item.path("id").asString()), item);
        call("SELECT pgstac.update_item(?::jsonb)", mapper.writeValueAsString(item));
    }

    @Override
    public void deleteItem(String collectionId, String itemId) throws IOException {
        getVersion();
        call("SELECT pgstac.delete_item(?, ?)", itemId, collectionId);
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

    /** Maps PostgreSQL/PgSTAC errors to HTTP status, per the facade error translation table (design notes §1c). */
    static PgStacException translate(SQLException e) {
        String sqlState = e.getSQLState() == null ? "" : e.getSQLState();
        String message = e.getMessage();
        if (sqlState.equals("23505")) {
            return new PgStacException(409, "Conflict: " + message, e);
        }
        if (sqlState.equals("23503")) {
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

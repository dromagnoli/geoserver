/* (c) 2026 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.pgstac;

import java.io.IOException;
import tools.jackson.databind.node.ObjectNode;

/**
 * Thin JDBC facade over the PgSTAC PostgreSQL functions: every read/write goes through exactly one {@code pgstac.*}
 * function call, except {@link #getCollectionPrivate} / {@link #setCollectionPrivate}, which read and write
 * {@code collections.private} directly since no PgSTAC function exposes it.
 */
public interface PgStacFacade {

    ObjectNode search(ObjectNode request) throws IOException;

    ObjectNode getCollection(String id) throws IOException;

    ObjectNode collectionSearch(ObjectNode request) throws IOException;

    ObjectNode getItem(String collectionId, String itemId) throws IOException;

    ObjectNode getQueryables(String collectionId) throws IOException;

    /** Result of {@code pgstac.get_version()}, checked against the supported range on first use. */
    String getVersion() throws IOException;

    /** Operator-only metadata stored in {@code collections.private}, never returned by the STAC API. */
    ObjectNode getCollectionPrivate(String id) throws IOException;

    void setCollectionPrivate(String id, ObjectNode metadata) throws IOException;

    void createCollection(ObjectNode collection) throws IOException;

    /**
     * Keeps the stored {@link GeoServerMetadata} entries the payload does not carry; read and write are not atomic, so
     * concurrent updates of the same collection follow last-write-wins.
     */
    void updateCollection(ObjectNode collection) throws IOException;

    void deleteCollection(String id) throws IOException;

    void createItem(ObjectNode item) throws IOException;

    /**
     * Keeps the stored {@link GeoServerMetadata} entries the payload does not carry, matching assets by key; read and
     * write are not atomic, so concurrent updates of the same item follow last-write-wins.
     */
    void updateItem(ObjectNode item) throws IOException;

    void deleteItem(String collectionId, String itemId) throws IOException;
}

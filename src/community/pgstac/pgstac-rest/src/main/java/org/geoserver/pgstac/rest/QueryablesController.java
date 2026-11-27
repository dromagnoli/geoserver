/* (c) 2026 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.pgstac.rest;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import org.geoserver.pgstac.PgStacException;
import org.geoserver.pgstac.PgStacFacade;
import org.geoserver.rest.RestBaseController;
import org.geoserver.rest.RestException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * The PgSTAC queryables, one JSON object per {@code pgstac.queryables} row with the column names as keys. PgSTAC
 * creates and drops the matching item indexes itself when a queryable is written.
 */
@RestController
@RequestMapping(
        path = RestBaseController.ROOT_PATH + "/services/stac/queryables",
        produces = MediaType.APPLICATION_JSON_VALUE)
public class QueryablesController extends RestBaseController {

    private final PgStacFacade facade;

    private final JsonMapper mapper = new JsonMapper();

    public QueryablesController(PgStacFacade facade) {
        this.facade = facade;
    }

    /** Every registered queryable, as {@code {"queryables": [...]}}. */
    @GetMapping
    public ObjectNode getQueryables() throws IOException {
        return facade.getQueryableRows(null);
    }

    /** The rows of one property, one per set of collections; 404 when it has none. */
    @GetMapping("{name}")
    public ObjectNode getQueryable(@PathVariable String name) throws IOException {
        ObjectNode rows = facade.getQueryableRows(name);
        if (rows.path("queryables").isEmpty()) {
            throw new RestException("Queryable " + name + " not found", HttpStatus.NOT_FOUND);
        }
        return rows;
    }

    /** Registers the property, replacing its rows for the same collections; a {@code name} in the body is ignored. */
    @PutMapping(path = "{name}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public void putQueryable(@PathVariable String name, InputStream body) throws IOException {
        // read here, the XStream converter would claim an ObjectNode body
        JsonNode queryable;
        try {
            queryable = mapper.readTree(body);
        } catch (JacksonException e) {
            throw new RestException("The body is not valid JSON: " + e.getOriginalMessage(), HttpStatus.BAD_REQUEST);
        }
        if (!(queryable instanceof ObjectNode object)) {
            throw new RestException("The body must be a JSON object", HttpStatus.BAD_REQUEST);
        }
        facade.upsertQueryable(name, object);
    }

    /** Removes the row of the property for exactly the given collections, the global one when none are given. */
    @DeleteMapping("{name}")
    public void deleteQueryable(@PathVariable String name, @RequestParam(required = false) List<String> collections)
            throws IOException {
        facade.deleteQueryable(name, collections);
    }

    /** Answers with the status the PgSTAC error maps to and its message as plain text, as for a RestException. */
    @ExceptionHandler(PgStacException.class)
    public void handlePgStacException(PgStacException e, HttpServletResponse response) throws IOException {
        response.setStatus(e.getHttpStatus());
        response.setContentType(MediaType.TEXT_PLAIN_VALUE);
        response.getWriter().write(e.getMessage());
    }
}

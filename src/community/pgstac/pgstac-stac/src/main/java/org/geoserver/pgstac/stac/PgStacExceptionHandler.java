/* (c) 2026 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.pgstac.stac;

import static org.geoserver.ogcapi.APIException.CONFLICT;
import static org.geoserver.ogcapi.APIException.INVALID_PARAMETER_VALUE;
import static org.geoserver.ogcapi.APIException.NOT_FOUND;
import static org.geoserver.ogcapi.APIException.NO_APPLICABLE_CODE;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.util.logging.Level;
import org.geoserver.config.GeoServer;
import org.geoserver.ogcapi.APIRequestInfo;
import org.geoserver.ogcapi.AbstractAPIExceptionHandler;
import org.geoserver.pgstac.PgStacException;
import org.geoserver.platform.ExtensionPriority;
import org.geoserver.platform.Service;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Writes the errors of {@link PgStacService} as STAC API {@code {code, description}} objects, a {@link PgStacException}
 * with the HTTP status it carries.
 */
@Component
public class PgStacExceptionHandler extends AbstractAPIExceptionHandler implements ExtensionPriority {

    private static final JsonMapper MAPPER = new JsonMapper();

    public PgStacExceptionHandler(GeoServer geoServer) {
        super(geoServer);
    }

    /** Matches on the service bean rather than its name, which can be overridden and is also used by OSEO STAC. */
    @Override
    public boolean canHandle(Throwable t, APIRequestInfo request) {
        Service service = request.getService();
        return service != null && service.getService() instanceof PgStacService;
    }

    @Override
    public void handle(Throwable t, HttpServletResponse response) {
        if (!(t instanceof PgStacException e)) {
            super.handle(t, response);
            return;
        }
        String code =
                switch (e.getHttpStatus()) {
                    case 400 -> INVALID_PARAMETER_VALUE;
                    case 404 -> NOT_FOUND;
                    case 409 -> CONFLICT;
                    default -> NO_APPLICABLE_CODE;
                };
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setStatus(e.getHttpStatus());
        writeResponse(response, e, code, e.getMessage());
    }

    @Override
    protected void writeResponse(HttpServletResponse response, Throwable t, String type, String title) {
        ObjectNode error = MAPPER.createObjectNode().put("code", type).put("description", title);
        try (OutputStream os = response.getOutputStream()) {
            MAPPER.writeValue(os, error);
        } catch (IOException | JacksonException ex) {
            LOGGER.log(Level.INFO, "Could not write the error back to the client", ex);
        }
    }

    /** Ahead of the generic OGC API handler, safe as {@link #canHandle} only takes this service. */
    @Override
    public int getPriority() {
        return ExtensionPriority.HIGHEST;
    }
}

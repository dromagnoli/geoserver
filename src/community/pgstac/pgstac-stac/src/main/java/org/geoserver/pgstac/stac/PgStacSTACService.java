/* (c) 2026 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.pgstac.stac;

import org.geoserver.config.GeoServer;
import org.geoserver.ogcapi.APIService;
import org.geoserver.pgstac.PgStacServiceInfo;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * STAC API 1.0 service backed by PgSTAC, named {@code STAC} at {@code ogc/stac/v1} unless the
 * {@code pgstac.service.name} and {@code pgstac.service.path} properties override them.
 */
@APIService(
        service = PgStacSTACService.SERVICE_NAME,
        version = "1.0.0",
        landingPage = PgStacSTACService.SERVICE_PATH,
        serviceClass = PgStacServiceInfo.class)
@RequestMapping(path = PgStacSTACService.SERVICE_PATH)
public class PgStacSTACService {

    // resolved from system properties or environment variables, so it can run next to the OSEO STAC
    static final String SERVICE_NAME = "${pgstac.service.name:STAC}";
    static final String SERVICE_PATH = "${pgstac.service.path:ogc/stac/v1}";

    static final String STAC_VERSION = "1.0.0";

    private final GeoServer geoServer;

    public PgStacSTACService(GeoServer geoServer) {
        this.geoServer = geoServer;
    }

    /** Returns the global service configuration, used by GeoServer to check the service is enabled. */
    public PgStacServiceInfo getServiceInfo() {
        return geoServer.getService(PgStacServiceInfo.class);
    }

    /** Returns the root STAC catalog; links and conformance classes are still empty. */
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
        catalog.putArray("conformsTo");
        catalog.putArray("links");
        return catalog;
    }
}

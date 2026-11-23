/* (c) 2026 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.pgstac.stac;

import static org.junit.Assert.assertEquals;

import com.jayway.jsonpath.DocumentContext;
import org.geoserver.ogcapi.OGCApiTestSupport;
import org.geoserver.platform.Service;
import org.junit.Test;

/** Checks the service registers with its default name and path. */
public class PgStacSTACServiceTest extends OGCApiTestSupport {

    @Test
    public void testRegisteredAsService() {
        Service service = applicationContext.getBean("pgstacServiceFactory", Service.class);
        assertEquals("STAC", service.getId());
        assertEquals("../ogc/stac/v1", service.getCustomCapabilitiesLink());
    }

    @Test
    public void testLandingPage() throws Exception {
        DocumentContext json = getAsJSONPath("ogc/stac/v1", 200);
        assertEquals("Catalog", json.read("type"));
        assertEquals("1.0.0", json.read("stac_version"));
    }
}

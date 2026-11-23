/* (c) 2026 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.pgstac.stac;

import static org.junit.Assert.assertEquals;

import org.geoserver.config.GeoServer;
import org.geoserver.ogcapi.OGCApiTestSupport;
import org.geoserver.pgstac.PgStacServiceInfo;
import org.geoserver.platform.Service;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletResponse;

/** Checks the coexistence setup, with the service renamed and moved next to the OSEO STAC one. */
public class PgStacSTACServiceRenamedTest extends OGCApiTestSupport {

    @BeforeClass
    public static void setOverrides() {
        // read when the Spring context starts, which happens before the first test
        System.setProperty("pgstac.service.name", "PgSTAC");
        System.setProperty("pgstac.service.path", "ogc/pgstac/v1");
    }

    @AfterClass
    public static void clearOverrides() {
        System.clearProperty("pgstac.service.name");
        System.clearProperty("pgstac.service.path");
    }

    @Test
    public void testRenamed() throws Exception {
        assertEquals(
                "PgSTAC",
                applicationContext
                        .getBean("pgstacServiceFactory", Service.class)
                        .getId());
        assertEquals("Catalog", getAsJSONPath("ogc/pgstac/v1", 200).read("type"));
        assertEquals(404, getAsServletResponse("ogc/stac/v1").getStatus());
    }

    @Test
    public void testDisabled() throws Exception {
        GeoServer gs = getGeoServer();
        PgStacServiceInfo info = gs.getService(PgStacServiceInfo.class);
        info.setEnabled(false);
        gs.save(info);
        try {
            MockHttpServletResponse response = getAsServletResponse("ogc/pgstac/v1");
            assertEquals(404, response.getStatus());
            assertEquals("Service PgSTAC is disabled", response.getErrorMessage());
        } finally {
            info.setEnabled(true);
            gs.save(info);
        }
    }
}

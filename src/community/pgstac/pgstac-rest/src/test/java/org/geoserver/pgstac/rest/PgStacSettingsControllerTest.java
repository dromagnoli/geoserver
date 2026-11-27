/* (c) 2026 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.pgstac.rest;

import static org.geoserver.pgstac.PgStacServiceInfo.ServiceLevel.DISABLED;
import static org.geoserver.pgstac.PgStacServiceInfo.ServiceLevel.TRANSACTIONAL;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;

import com.jayway.jsonpath.JsonPath;
import java.util.ArrayList;
import java.util.Set;
import org.geoserver.config.GeoServer;
import org.geoserver.pgstac.AccessRule;
import org.geoserver.pgstac.PgStacServiceInfo;
import org.junit.Before;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletResponse;

public class PgStacSettingsControllerTest extends PgStacRestTestSupport {

    @Before
    public void reset() {
        GeoServer gs = getGeoServer();
        PgStacServiceInfo info = gs.getService(PgStacServiceInfo.class);
        info.setServiceLevel(DISABLED);
        info.setMaxFeatures(500);
        info.setCollectionAccessRules(new ArrayList<>());
        info.setConnectionParameters(PGSTAC.getConnectionParameters());
        gs.save(info);
    }

    @Test
    public void testPasswordNotInClear() throws Exception {
        String json = getAsString(REST + "settings.json");
        assertEquals("STAC", JsonPath.read(json, "$.pgstac.name"));
        assertThat(json, not(containsString(password())));

        String html = getAsString(REST + "settings.html");
        assertThat(html, containsString("Service level:  \"DISABLED\""));
        assertThat(html, not(containsString(password())));
    }

    @Test
    public void testRoundTripKeepsConnection() throws Exception {
        String xml = getAsString(REST + "settings.xml");
        assertThat(xml, not(containsString(password())));
        xml = xml.replace("<serviceLevel>DISABLED</serviceLevel>", "<serviceLevel>TRANSACTIONAL</serviceLevel>");
        assertEquals(
                200,
                putAsServletResponse(REST + "settings", xml, "application/xml").getStatus());

        PgStacServiceInfo info = getGeoServer().getService(PgStacServiceInfo.class);
        assertEquals(TRANSACTIONAL, info.getServiceLevel());
        assertEquals(password(), info.getConnectionParameters().get("passwd"));
        assertEquals(200, getAsServletResponse(REST + "queryables").getStatus());
    }

    @Test
    public void testPartialUpdate() throws Exception {
        String xml =
                """
                <pgstac>
                  <collectionAccessRules>
                    <pgstacAccessRule>
                      <cqlFilter>"id" = 'restricted'</cqlFilter>
                      <roles><string>ROLE_S2</string></roles>
                    </pgstacAccessRule>
                  </collectionAccessRules>
                </pgstac>""";
        assertEquals(
                200,
                putAsServletResponse(REST + "settings", xml, "application/xml").getStatus());

        PgStacServiceInfo info = getGeoServer().getService(PgStacServiceInfo.class);
        AccessRule rule = info.getCollectionAccessRules().get(0);
        assertEquals("\"id\" = 'restricted'", rule.getCqlFilter());
        assertEquals(Set.of("ROLE_S2"), rule.getRoles());
        // the members the body leaves out stay as they were
        assertEquals(500, info.getMaxFeatures());
        assertEquals(DISABLED, info.getServiceLevel());
        assertEquals(password(), info.getConnectionParameters().get("passwd"));
    }

    @Test
    public void testNoWorkspaceSettings() throws Exception {
        String xml = "<pgstac><serviceLevel>TRANSACTIONAL</serviceLevel></pgstac>";
        MockHttpServletResponse put = putAsServletResponse(REST + "workspaces/sf/settings", xml, "application/xml");
        assertEquals(405, put.getStatus());
        assertEquals(
                404, getAsServletResponse(REST + "workspaces/sf/settings.xml").getStatus());
    }

    private static String password() {
        return (String) PGSTAC.getConnectionParameters().get("passwd");
    }
}

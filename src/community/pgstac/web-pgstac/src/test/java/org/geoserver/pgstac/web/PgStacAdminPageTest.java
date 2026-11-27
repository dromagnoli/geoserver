/* (c) 2026 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.pgstac.web;

import static org.geoserver.pgstac.PgStacServiceInfo.ServiceLevel.TRANSACTIONAL;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.Assert.assertEquals;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import org.apache.wicket.feedback.FeedbackMessage;
import org.apache.wicket.util.tester.FormTester;
import org.geoserver.config.GeoServer;
import org.geoserver.pgstac.PgStacServiceInfo;
import org.geoserver.pgstac.PgStacTestResource;
import org.geoserver.web.GeoServerWicketTestSupport;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

public class PgStacAdminPageTest extends GeoServerWicketTestSupport {

    @ClassRule
    public static final PgStacTestResource PGSTAC = new PgStacTestResource();

    private static final String PANEL = "tabs:panel:initial:";

    private static final String TEST_LINK = "form:" + PANEL + "connectionForm:test";

    @Before
    public void reset() {
        setConnection(PGSTAC.getConnectionParameters());
        login();
    }

    @Test
    public void testSave() throws Exception {
        openServiceTab();
        FormTester form = tester.newFormTester("form");
        form.setValue(PANEL + "maxFeatures", "50");
        form.select(PANEL + "serviceLevel", 1);
        form.submit("apply");
        tester.assertNoErrorMessage();

        PgStacServiceInfo info = getGeoServer().getService(PgStacServiceInfo.class);
        assertEquals(50, info.getMaxFeatures());
        assertEquals(TRANSACTIONAL, info.getServiceLevel());
        // the connection went through the PostGIS form and came back unchanged
        Map<String, Serializable> expected = PGSTAC.getConnectionParameters();
        assertEquals(expected.get("passwd"), info.getConnectionParameters().get("passwd"));
        assertEquals(expected.get("database"), info.getConnectionParameters().get("database"));
    }

    @Test
    public void testConnectionCheck() throws Exception {
        openServiceTab();
        tester.clickLink(TEST_LINK, true);
        tester.assertNoErrorMessage();
        assertThat(messages(FeedbackMessage.INFO), contains(startsWith("Connected, PgSTAC version 0.10.")));
    }

    @Test
    public void testConnectionCheckFails() throws Exception {
        Map<String, Serializable> params = PGSTAC.getConnectionParameters();
        params.put("passwd", "wrong");
        setConnection(params);

        openServiceTab();
        tester.clickLink(TEST_LINK, true);
        assertThat(messages(FeedbackMessage.ERROR), contains(startsWith("Connection failed: ")));
    }

    private void openServiceTab() {
        tester.startPage(PgStacAdminPage.class);
        tester.clickLink("form:tabs:tabs-container:tabs:1:link");
    }

    private List<String> messages(int level) {
        return tester.getMessages(level).stream().map(Object::toString).toList();
    }

    private void setConnection(Map<String, Serializable> params) {
        GeoServer gs = getGeoServer();
        PgStacServiceInfo info = gs.getService(PgStacServiceInfo.class);
        info.setConnectionParameters(params);
        gs.save(info);
    }
}

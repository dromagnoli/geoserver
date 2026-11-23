/* (c) 2026 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.ogcapi;

import static org.junit.Assert.assertEquals;

import com.thoughtworks.xstream.XStream;
import java.io.File;
import java.io.FileOutputStream;
import java.util.List;
import org.geoserver.config.GeoServerInfo;
import org.geoserver.config.util.XStreamPersisterFactory;
import org.geoserver.data.test.SystemTestData;
import org.geoserver.platform.Service;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

/** Checks the {@link APIService} and request mapping placeholders resolve to the configured values. */
public class PlaceholderServiceTest extends OGCApiTestSupport {

    @BeforeClass
    public static void setPlaceholders() {
        // read when the Spring context starts, which happens before the first test
        System.setProperty("ogcapi.placeholder.service", "Renamed");
        System.setProperty("ogcapi.placeholder.path", "ogc/renamed/v1");
    }

    @AfterClass
    public static void clearPlaceholders() {
        System.clearProperty("ogcapi.placeholder.service");
        System.clearProperty("ogcapi.placeholder.path");
    }

    @Override
    protected void setUpSpring(List<String> springContextLocations) {
        super.setUpSpring(springContextLocations);
        springContextLocations.add("classpath:/org/geoserver/ogcapi/placeholderContext.xml");
    }

    @Override
    protected void setUpTestData(SystemTestData testData) throws Exception {
        super.setUpTestData(testData);

        // without trailing slash match the landing page slash is handled by LandingPageSlashFilter
        File file = new File(testData.getDataDirectoryRoot(), "global.xml");
        XStream xs = new XStreamPersisterFactory().createXMLPersister().getXStream();
        GeoServerInfo gs = (GeoServerInfo) xs.fromXML(file);
        gs.setTrailingSlashMatch(false);
        try (FileOutputStream fos = new FileOutputStream(file)) {
            xs.toXML(gs, fos);
        }
    }

    @Test
    public void testResolvedNameAndPath() throws Exception {
        assertEquals(
                "Renamed ogc/renamed/v1",
                getAsJSONPath("ogc/renamed/v1/info", 200).read("message"));
        assertEquals(404, getAsServletResponse("ogc/placeholder/v1/info").getStatus());
        assertEquals(
                "Renamed",
                applicationContext
                        .getBean("placeholderServiceFactory", Service.class)
                        .getId());
    }

    @Test
    public void testLandingPageWithTrailingSlash() throws Exception {
        assertEquals(
                "Placeholder landing page",
                getAsJSONPath("ogc/renamed/v1/", 200).read("message"));
    }
}

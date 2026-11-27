/* (c) 2026 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.pgstac.rest;

import org.geoserver.config.GeoServer;
import org.geoserver.data.test.SystemTestData;
import org.geoserver.pgstac.PgStacServiceInfo;
import org.geoserver.pgstac.PgStacTestResource;
import org.geoserver.test.GeoServerSystemTestSupport;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;

/** Runs the REST API as administrator against a PgSTAC container. */
public abstract class PgStacRestTestSupport extends GeoServerSystemTestSupport {

    @ClassRule
    public static final PgStacTestResource PGSTAC = new PgStacTestResource();

    protected static final String REST = "/rest/services/stac/";

    @Override
    protected void setUpTestData(SystemTestData testData) throws Exception {
        // the data comes from PgSTAC, no catalog layers needed
        testData.setUpSecurity();
    }

    @Override
    protected void onSetUp(SystemTestData testData) throws Exception {
        super.onSetUp(testData);
        GeoServer gs = getGeoServer();
        PgStacServiceInfo info = gs.getService(PgStacServiceInfo.class);
        info.setConnectionParameters(PGSTAC.getConnectionParameters());
        gs.save(info);
    }

    @Before
    public void admin() {
        loginAsAdmin();
    }

    @After
    public void cleanUp() {
        logout();
    }
}

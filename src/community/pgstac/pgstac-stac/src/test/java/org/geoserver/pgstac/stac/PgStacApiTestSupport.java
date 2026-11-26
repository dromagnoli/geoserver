/* (c) 2026 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.pgstac.stac;

import org.geoserver.config.GeoServer;
import org.geoserver.data.test.SystemTestData;
import org.geoserver.ogcapi.OGCApiTestSupport;
import org.geoserver.pgstac.DefaultPgStacFacade;
import org.geoserver.pgstac.PgStacFacade;
import org.geoserver.pgstac.PgStacServiceInfo;
import org.geoserver.pgstac.PgStacTestResource;
import org.junit.ClassRule;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/** Runs the STAC API against a PgSTAC container, filled by each test class through {@link #loadData}. */
public abstract class PgStacApiTestSupport extends OGCApiTestSupport {

    @ClassRule
    public static final PgStacTestResource PGSTAC = new PgStacTestResource();

    protected static final String ROOT = "http://localhost:8080/geoserver/";

    protected static final String BASE = ROOT + "ogc/stac/v1/";

    /** A collection with the given id and only the members PgSTAC requires. */
    protected static final String COLLECTION =
            """
            {"type": "Collection", "id": "%s", "stac_version": "1.0.0", "description": "test", "license": "proprietary",
             "links": [],
             "extent": {"spatial": {"bbox": [[-180, -90, 180, 90]]},
                        "temporal": {"interval": [["2024-01-01T00:00:00Z", null]]}}}
            """;

    private static final JsonMapper MAPPER = new JsonMapper();

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
        configure(info);
        gs.save(info);
        // unsecured, so the tests can load what the API hides
        loadData(applicationContext.getBean(DefaultPgStacFacade.class));
    }

    /** Adds service settings, such as access rules, before they are saved. */
    protected void configure(PgStacServiceInfo info) {}

    /** Loads the collections and items of the test class into the empty PgSTAC database. */
    protected abstract void loadData(PgStacFacade facade) throws Exception;

    /** Parses a JSON object from a {@link String#formatted} template. */
    protected static ObjectNode json(String template, Object... args) {
        return (ObjectNode) MAPPER.readTree(template.formatted(args));
    }
}

/* (c) 2024 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.postgres;

import java.util.Collections;
import java.util.Map;
import org.geotools.data.postgis.PostgisNGDataStoreFactory;
import org.geotools.jdbc.JDBCDataStore;
import org.geotools.jdbc.SQLDialect;

public class EMSAPostgresDataStoreFactory extends PostgisNGDataStoreFactory {

    public static final Param DBTYPE =
            new Param(
                    "dbtype",
                    String.class,
                    "Type",
                    true,
                    "emsapostgis",
                    Collections.singletonMap("level", "program"));

    @Override
    protected SQLDialect createSQLDialect(JDBCDataStore dataStore, Map<String, ?> params) {
        return new EMSAPostgresSQLDialect(dataStore, params);
    }

    @Override
    protected void setupParameters(Map<String, Object> parameters) {
        super.setupParameters(parameters);
        parameters.put(DBTYPE.key, DBTYPE);
    }

    @Override
    protected String getDatabaseID() {
        return (String) DBTYPE.sample;
    }

    @Override
    public String getDescription() {
        return "EMSA PostGIS extended database";
    }

    @Override
    public String getDisplayName() {
        return "EMSA PostGIS";
    }
}

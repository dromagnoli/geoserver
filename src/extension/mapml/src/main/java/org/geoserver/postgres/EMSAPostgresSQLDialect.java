/* (c) 2024 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.postgres;

import org.geotools.data.jdbc.FilterToSQL;
import org.geotools.data.postgis.PostGISDialect;
import org.geotools.jdbc.JDBCDataStore;

import java.util.Map;

/**
 * TODO: Should we factor out from PostgreSqlDialect from rtmps storage
 * */
public class EMSAPostgresSQLDialect extends PostGISDialect {

    private JDBCDataStore jdbcDataStore;

    public EMSAPostgresSQLDialect(JDBCDataStore dataStore, Map<String, ?> params) {
        super(dataStore);
        this.jdbcDataStore = dataStore;
    }



    @Override
    public FilterToSQL createFilterToSQL() {
        return new EMSAPostgresFilterToSQL(this, jdbcDataStore);
    }

}
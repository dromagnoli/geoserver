package org.geoserver.postgres;

import org.geotools.data.jdbc.FilterToSQL;
import org.geotools.data.postgis.PostGISDialect;
import org.geotools.jdbc.JDBCDataStore;
import org.geotools.jdbc.PreparedFilterToSQL;

import java.util.Map;

/**
 * TODO: Should we factor out from PostgreSqlDialect from rtmps storage
 * */
public class EMSAPostgresSQLDialect extends PostGISDialect {

    private static final String NULL_CHAR = "\u0000";

    public EMSAPostgresSQLDialect(JDBCDataStore dataStore, Map<String, ?> params) {
        super(dataStore);
    }

    @Override
    public FilterToSQL createFilterToSQL() {
        return new EMSAPostgresFilterToSQL(this);
    }

}
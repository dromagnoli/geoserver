/* (c) 2024 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.postgres;

import org.geotools.api.data.Transaction;
import org.geotools.api.filter.PropertyIsEqualTo;
import org.geotools.api.filter.expression.Expression;
import org.geotools.api.filter.expression.Function;
import org.geotools.api.filter.expression.Literal;
import org.geotools.data.postgis.PostgisFilterToSQL;
import org.geotools.filter.FilterCapabilities;
import org.geotools.jdbc.JDBCDataStore;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

public class EMSAPostgresFilterToSQL extends PostgisFilterToSQL {

    public enum SearchType {
        TEXT, NUMERIC, MIXED
    }

    private JDBCDataStore datastore;

    public EMSAPostgresFilterToSQL(EMSAPostgresSQLDialect dialect, JDBCDataStore jdbcDataStore) {
        super(dialect);
        this.datastore = jdbcDataStore;
    }

    @Override
    protected FilterCapabilities createFilterCapabilities() {
        FilterCapabilities filterCapabilities = super.createFilterCapabilities();
        filterCapabilities.addType(FullTextSearchFunction.class);
        return filterCapabilities;
    }

    @Override
    public Object visit(PropertyIsEqualTo filter, Object extraData) {
        FullTextSearchFunction fullTextSearchFunction = getSelectedFullTextSearchFunction(filter);
        if (fullTextSearchFunction != null) {
            return visit(fullTextSearchFunction, extraData);
        } else {
            return super.visit(filter, extraData);
        }
    }

    @Override
    public Object visit(Function function, Object extraData) {
        if (function instanceof FullTextSearchFunction) {
            try {
                String searchText = function.getParameters().get(0).evaluate(null, String.class);
                // Replace : and " " so that we can retrieve timestamps
                //searchText = searchText.replaceAll("[: ]", "_");
                //out.write(String.format("(tsv_content @@ to_tsquery('english', '%s')", searchText));
                out.write("(");
                // Append additional WHERE clause for numeric columns
                String likeClause = generateLikeClause((FullTextSearchFunction) function, searchText);
                if (!likeClause.isEmpty()) {
                    //out.write(" OR ");
                    out.write(likeClause);
                    if (LOGGER.isLoggable(Level.FINE)) {
                        LOGGER.fine("LIKE clause: " + likeClause);
                    }
                }
                out.write(")");
            } catch (IOException ioe) {
                throw new RuntimeException(IO_ERROR, ioe);
            }

            return extraData;
        }
        return super.visit(function, extraData);
    }

    private String generateLikeClause(FullTextSearchFunction function, String searchText) {
        List<String> castedColumns = getCastedColumns(featureType.getTypeName(), searchText);
        StringBuilder likeClause = new StringBuilder();

        for (int i = 0; i < castedColumns.size(); i++) {
            if (i > 0) {
                likeClause.append(" OR ");
            }
            likeClause.append(String.format("CAST(%s AS TEXT) ILIKE '%%%s%%'", castedColumns.get(i), searchText));
        }
        return likeClause.toString();
    }

    private List<String> getCastedColumns(String tableName, String searchText) {
        List<String> castedColumns = new ArrayList<>();
        SearchType type = getSearchType(searchText);

        try (Connection connection = datastore.getConnection(Transaction.AUTO_COMMIT)) {
            DatabaseMetaData metaData = connection.getMetaData();
            try (ResultSet columns = metaData.getColumns(null, datastore.getDatabaseSchema(), tableName, null)) {
                while (columns.next()) {
                    String columnName = columns.getString("COLUMN_NAME");
                    String columnType = columns.getString("TYPE_NAME").toLowerCase();
                    // casting varchars to text has no significant impact on performances.

                    if (columnType.startsWith("varchar") || columnType.startsWith("text")
                        || (type == SearchType.NUMERIC && isNumericType(columnType))
                        || (type != SearchType.TEXT && columnType.startsWith("timestamp"))) {
                            castedColumns.add(columnName);
                    }
                }
            }
        } catch (SQLException | IOException e) {
            throw new RuntimeException("Error retrieving columns to be casted", e);
        }
        return castedColumns;
    }

    private SearchType getSearchType(String str) {
        if (str == null || str.isEmpty()) {
            return SearchType.TEXT;
        }
        if (str.matches("-?\\d+(\\.\\d+)?")) {
            return SearchType.NUMERIC;
        } else if (str.matches(".*\\d+.*")){
            return SearchType.MIXED;
        }
        return  SearchType.TEXT;
    }

    private boolean isNumericType(String columnType) {
        return columnType.startsWith("int") ||
            columnType.equals("smallint") || columnType.equals("bigint") ||
            columnType.equals("decimal") || columnType.equals("numeric") ||
            columnType.equals("real") || columnType.startsWith("float") ||
            columnType.equals("double precision");
    }

    private FullTextSearchFunction getSelectedFullTextSearchFunction(PropertyIsEqualTo filter) {
        Expression expr1 = filter.getExpression1();
        Expression expr2 = filter.getExpression2();

        if (expr2 instanceof FullTextSearchFunction) {
            // switch position
            Expression tmp = expr1;
            expr1 = expr2;
            expr2 = tmp;
        }

        if (expr1 instanceof FullTextSearchFunction) {
            if (!(expr2 instanceof Literal)) {
                throw new UnsupportedOperationException(
                        "Unsupported usage of FullTextSearchFunction: it can be compared only to a Boolean \"true\" value");
            }

            Boolean nearest = (Boolean) evaluateLiteral((Literal) expr2, Boolean.class);
            if (nearest == null || !nearest.booleanValue()) {
                throw new UnsupportedOperationException(
                        "Unsupported usage of FullTextSearchFunction: it can be compared only to a Boolean \"true\" value");
            }

            return (FullTextSearchFunction) expr1;
        } else {
            return null;
        }
    }
}

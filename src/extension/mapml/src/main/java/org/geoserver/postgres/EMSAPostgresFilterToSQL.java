package org.geoserver.postgres;

import org.geotools.api.filter.PropertyIsEqualTo;
import org.geotools.api.filter.expression.Expression;
import org.geotools.api.filter.expression.Function;
import org.geotools.api.filter.expression.Literal;
import org.geotools.data.postgis.PostGISDialect;
import org.geotools.data.postgis.PostgisFilterToSQL;
import org.geotools.filter.FilterCapabilities;

import java.io.IOException;

public class EMSAPostgresFilterToSQL extends PostgisFilterToSQL {

    public EMSAPostgresFilterToSQL(PostGISDialect dialect) {
        super(dialect);
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
                out.write(String.format("tsv_content @@ to_tsquery('english', '%s')", searchText));
            } catch (IOException ioe) {
                throw new RuntimeException(IO_ERROR, ioe);
            }
            return extraData;
        }
        return super.visit(function, extraData);
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

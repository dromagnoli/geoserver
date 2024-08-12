package org.geoserver.postgres;

import org.geotools.api.filter.expression.Expression;
import org.geotools.api.filter.expression.VolatileFunction;
import org.geotools.filter.FunctionExpressionImpl;


import java.util.List;

public class FullTextSearchFunction extends FunctionExpressionImpl implements VolatileFunction {

    public FullTextSearchFunction() {
        super("full_text_search");
    }

    @Override
    public Object evaluate(Object feature) {
        // This method can be used to evaluate the function outside of SQL context,
        // e.g., for validation purposes.
        throw new UnsupportedOperationException("FullTextSearchFunction should be evaluated as SQL.");
    }

    @Override
    public void setParameters(List<Expression> parameters) {
        if (parameters.size() != 1) {
            throw new IllegalArgumentException("full_text_search function expects exactly one argument.");
        }
        super.setParameters(parameters);
    }
}

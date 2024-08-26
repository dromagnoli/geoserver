/* (c) 2024 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.postgres;

import java.util.List;
import org.geotools.api.filter.expression.Expression;
import org.geotools.api.filter.expression.VolatileFunction;
import org.geotools.filter.FunctionExpressionImpl;

public class FullTextSearchFunction extends FunctionExpressionImpl implements VolatileFunction {

    public FullTextSearchFunction() {
        super("fullTextSearch");
    }

    @Override
    public Object evaluate(Object feature) {
        // This method can be used to evaluate the function outside of SQL context,
        // e.g., for validation purposes.
        throw new UnsupportedOperationException(
                "FullTextSearchFunction should be evaluated as SQL.");
    }

    @Override
    public void setParameters(List<Expression> parameters) {
        if (parameters.size() != 1) {
            throw new IllegalArgumentException(
                    "FullTextSearchFunction expects exactly one argument.");
        }
        super.setParameters(parameters);
    }
}

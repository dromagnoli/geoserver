/* (c) 2026 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.pgstac;

import org.geotools.api.filter.ExcludeFilter;
import org.geotools.api.filter.Filter;
import org.geotools.api.filter.Id;
import org.geotools.api.filter.PropertyIsLike;
import org.geotools.api.filter.expression.Function;
import org.geotools.api.filter.expression.Literal;
import org.geotools.api.filter.spatial.Beyond;
import org.geotools.api.filter.spatial.DWithin;
import org.geotools.api.temporal.Period;
import org.geotools.filter.text.cqljson.FilterToCQL2Json;
import org.geotools.filter.text.ecql.ECQL;
import org.geotools.filter.visitor.DefaultFilterVisitor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/** Converts GeoTools filters to CQL2 JSON that PgSTAC can run, rejecting what it cannot run or would run wrongly. */
public class PgStacFilters {

    private static final JsonMapper MAPPER = new JsonMapper();

    private static final String FILTER_LANG = "filter-lang";

    private static final String CQL2_JSON = "cql2-json";

    private PgStacFilters() {}

    /**
     * Returns the CQL2 JSON for the filter, or {@code null} for {@link Filter#INCLUDE}.
     *
     * @throws PgStacException with status 400 if the filter uses a construct PgSTAC cannot run
     */
    public static ObjectNode toCQL2Json(Filter filter) throws PgStacException {
        if (filter == Filter.INCLUDE) return null;
        UnsupportedFinder finder = new UnsupportedFinder();
        filter.accept(finder, null);
        if (finder.unsupported != null) {
            throw new PgStacException(
                    400, "Filter not supported by PgSTAC, it uses " + finder.unsupported + ": " + ECQL.toCQL(filter));
        }
        return (ObjectNode) filter.accept(new FilterToCQL2Json(MAPPER), MAPPER.createObjectNode());
    }

    /**
     * ANDs a CQL2 JSON filter into a PgSTAC search request, in place; a {@code null} filter leaves it unchanged.
     *
     * @throws PgStacException with status 400 if the request filters with the Query extension or a non CQL2 JSON
     *     language
     */
    public static void and(ObjectNode request, ObjectNode filter) throws PgStacException {
        if (filter == null) return;
        // PgSTAC refuses "query" next to "filter", and an "and" around a CQL1 filter would change its meaning
        JsonNode lang = request.get(FILTER_LANG);
        if (request.has("query") || (lang != null && !CQL2_JSON.equals(lang.asString()))) {
            throw new PgStacException(400, "Only cql2-json filters are supported, request uses " + lang);
        }
        JsonNode existing = request.get("filter");
        if (existing != null) {
            ObjectNode and = MAPPER.createObjectNode().put("op", "and");
            and.putArray("args").add(existing).add(filter);
            filter = and;
        }
        request.set("filter", filter);
        request.put(FILTER_LANG, CQL2_JSON);
    }

    /**
     * Records a construct PgSTAC cannot run, or that {@link FilterToCQL2Json} would encode with a different meaning.
     */
    private static class UnsupportedFinder extends DefaultFilterVisitor {

        String unsupported;

        @Override
        public Object visit(ExcludeFilter filter, Object data) {
            unsupported = "EXCLUDE";
            return data;
        }

        @Override
        public Object visit(Id filter, Object data) {
            unsupported = "feature id filters";
            return data;
        }

        @Override
        public Object visit(Function expression, Object data) {
            // PgSTAC implements no CQL2 functions
            unsupported = "function " + expression.getName();
            return data;
        }

        @Override
        public Object visit(DWithin filter, Object data) {
            // CQL2 has no distance operator, the converter would emit s_within and drop the distance
            unsupported = "DWITHIN";
            return data;
        }

        @Override
        public Object visit(Beyond filter, Object data) {
            // same as DWITHIN, the converter would emit s_crosses
            unsupported = "BEYOND";
            return data;
        }

        @Override
        public Object visit(PropertyIsLike filter, Object data) {
            // the converter drops case-insensitivity and copies the pattern as-is, CQL2 wildcards are % and _
            if (!filter.isMatchingCase() || !"%".equals(filter.getWildCard()) || !"_".equals(filter.getSingleChar())) {
                unsupported = "case-insensitive or non-standard LIKE";
            }
            return super.visit(filter, data);
        }

        @Override
        public Object visit(Literal expression, Object data) {
            // converter bugs: period literals are dropped from the output, whole numbers are truncated to int
            Object value = expression.getValue();
            if (value instanceof Period) {
                unsupported = "time interval literals";
            } else if (value instanceof Number n && n.doubleValue() % 1 == 0 && isBeyondInt(n.doubleValue())) {
                unsupported = "integers beyond 32 bits";
            }
            return data;
        }

        private static boolean isBeyondInt(double value) {
            return value > Integer.MAX_VALUE || value < Integer.MIN_VALUE;
        }
    }
}

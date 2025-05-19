/* (c) 2025 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.wps.gs.download;

import org.geoserver.catalog.CoverageInfo;
import org.geoserver.wcs2_0.response.DimensionBean;
import org.geotools.api.data.Query;
import org.geotools.api.filter.And;
import org.geotools.api.filter.BinaryComparisonOperator;
import org.geotools.api.filter.Filter;
import org.geotools.api.filter.FilterFactory;
import org.geotools.api.filter.Or;
import org.geotools.api.filter.PropertyIsEqualTo;
import org.geotools.api.filter.PropertyIsGreaterThan;
import org.geotools.api.filter.PropertyIsGreaterThanOrEqualTo;
import org.geotools.api.filter.PropertyIsLessThan;
import org.geotools.api.filter.PropertyIsLessThanOrEqualTo;
import org.geotools.api.filter.expression.Expression;
import org.geotools.api.filter.expression.PropertyName;
import org.geotools.coverage.grid.GridCoverage2D;
import org.geotools.coverage.grid.GridCoverageFactory;
import org.geotools.coverage.grid.io.DimensionDescriptor;
import org.geotools.coverage.grid.io.GranuleSource;
import org.geotools.coverage.grid.io.GridCoverage2DReader;
import org.geotools.coverage.grid.io.StructuredGridCoverage2DReader;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.factory.CommonFactoryFinder;
import org.geotools.feature.visitor.UniqueVisitor;
import org.geotools.filter.visitor.DefaultFilterVisitor;
import org.geotools.filter.visitor.DuplicatingFilterVisitor;
import org.geotools.util.DateRange;
import org.geotools.util.NumberRange;
import org.geotools.util.Utilities;
import org.geotools.util.logging.Logging;

import java.io.IOException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;


/**
 * Manages the slicing and filtering of spatiotemporal grid coverage data.
 *
 * This class provides functionality to extract and process dimensional slices from grid coverages,
 * transforming a Filter to a List of Filter needed to extract 2D slices from a SpatioTemporal
 * grid coverage.
 *
 */
class SpatioTemporalCoverageSlicer {

    static class FilterSlicer {
        private String propertyName;
        private DimensionDescriptor dimensionDescriptor;
        private UniqueVisitor visitor;
        private List<Comparable> slices;

        public FilterSlicer(DimensionDescriptor dimensionDescriptor) {
            this.dimensionDescriptor = dimensionDescriptor;
            propertyName = dimensionDescriptor.getStartAttribute();
            visitor = new UniqueVisitor(FF.property(propertyName));
        }

        /**
         * Extracts distinct temporal or spatial slices from the provided granules.
         * The slicing is based on the internal filter logic defined in this slicer.
         *
         * @param granules The input FeatureCollection
         * @return A list of unique slice values from the provided collection,
         * on the slicing attribute.
         */
        public List<Comparable> extractSlices(SimpleFeatureCollection granules) throws IOException {
            granules.accepts(visitor, null);
            slices = visitor.getResult().toList();
            Collections.sort(slices);
            return slices;
        }

        /**
         * Returns the list of extracted slice values after processing.
         *
         * @return List of comparable slice values.
         */
        public List<Comparable> getSlices() {
            return slices;
        }
    }

    private static final GridCoverageFactory GC_FACTORY = new GridCoverageFactory();
    private static final Logger LOGGER = Logging.getLogger(SpatioTemporalCoverageSlicer.class);
    private static final FilterFactory FF = CommonFactoryFinder.getFilterFactory(null);

    private GridCoverage2DReader reader;
    private CoverageInfo coverageInfo;
    private Filter filter;
    private List<FilterSlicer> filterSlicers;

    public SpatioTemporalCoverageSlicer(GridCoverage2DReader reader, CoverageInfo coverageInfo, Filter filter) {
        this.reader = reader;
        this.coverageInfo = coverageInfo;
        this.filter = filter;
    }

    /**
     * Generates the Cartesian product of slice values from multiple FilterSlicers.
     *
     * This method creates a list of maps representing all possible combinations
     * of slice values across different dimensions. Each map in the result represents
     * a unique combination of slice values from the provided FilterSlicers.
     *
     * @param slicers A list of FilterSlicer instances to generate combinations from
     * @return A list of maps, where each map contains slice values for different dimensions
     */
    private <T extends Comparable<T>> List<Map<String, Object>> cartesianProduct(List<FilterSlicer> slicers) {
        List<Map<String, Object>> results = new ArrayList<>();
        Map<String, Comparable> current = new HashMap<>();
        cartesianProductHelper(slicers, 0, current, results);
        return results;
    }

    /**
     * Recursively generates the Cartesian product of slice values for FilterSlicers.
     *
     * This helper method builds all possible combinations of slice values across different
     * dimensions by recursively traversing the list of FilterSlicers. It populates the
     * 'slices' list with unique combinations of slice values.
     *
     * @param slicers List of FilterSlicers to generate combinations from
     * @param index Current index in the slicers list being processed
     * @param current Map of current slice values being built
     * @param slices List to store all generated slice value combinations
     */
    @SuppressWarnings("unchecked")
    private <T extends Comparable<T>>  void cartesianProductHelper(List<FilterSlicer> slicers, int index, Map<String, T> current, List<Map<String, Object>> slices) {
        if (index == slicers.size()) {
            slices.add(new LinkedHashMap<>(current));
            return;
        }

        FilterSlicer slicer = slicers.get(index);
        for (Comparable<?> value : slicer.getSlices()) {
            current.put(slicer.propertyName, (T) value);
            cartesianProductHelper(slicers, index + 1, current, slices);
        }
    }

    /**
     * Removes range comparison filters on specified dimension names from the original filter.
     *
     * This method traverses the filter hierarchy and eliminates range comparison filters
     * (less than, less than or equal, greater than, greater than or equal) that apply
     * to the given dimension names. This will be useful when slicing a coverage so that
     * the ranges filtering get replaced by single slice filters.
     *
     * @param original The original filter to be processed
     * @param dimensionNames Set of dimension names to check for range comparisons
     * @return A modified filter with range comparisons removed
     */
    private Filter removeRangeComparisons(Filter original, Set<String> dimensionNames) {
        return (Filter) original.accept(new DuplicatingFilterVisitor() {
            @Override
            public Object visit(And filter, Object data) {
                List<Filter> newChildren = new ArrayList<>();
                for (Filter child : filter.getChildren()) {
                    if (isRangeComparisonOnDimension(child, dimensionNames)) {
                        LOGGER.log(Level.FINER, "Removing Range Comparison on filter:" + filter);
                        continue;
                    }
                    newChildren.add((Filter) child.accept(this, data));
                }
                if (newChildren.isEmpty()) {
                    return Filter.INCLUDE;
                }
                return FF.and(newChildren);
            }

            @Override
            public Object visit(Or filter, Object data) {
                List<Filter> newChildren = new ArrayList<>();
                for (Filter child : filter.getChildren()) {
                    newChildren.add((Filter) child.accept(this, data));
                }
                if (newChildren.isEmpty()) {
                    return Filter.EXCLUDE;
                }
                return FF.or(newChildren);
            }

            private boolean isRangeComparisonOnDimension(Filter f, Set<String> dimensions) {
                if (f instanceof BinaryComparisonOperator) {
                    BinaryComparisonOperator bco = (BinaryComparisonOperator) f;
                    Expression expr1 = bco.getExpression1();
                    if (expr1 instanceof PropertyName &&
                            dimensions.contains(((PropertyName) expr1).getPropertyName())) {
                        return bco instanceof PropertyIsLessThan ||
                                bco instanceof PropertyIsLessThanOrEqualTo ||
                                bco instanceof PropertyIsGreaterThan ||
                                bco instanceof PropertyIsGreaterThanOrEqualTo;
                    }
                }
                return false;
            }

        }, null);
    }

    /**
     * Initializes filter slicers for a structured grid coverage reader.
     *
     * Prepares dimension-based filtering for a coverage by:
     * - Identifying dimension attributes from a structured grid coverage reader
     * - Extracting filter properties from the current filter
     * - Creating filter slicers for matching dimensions
     *
     * @return true if filter slicers were successfully created, false otherwise, meaning the coverage
     * won't be a Multidimensional gridCoverage.
     * @throws IOException if there are issues accessing granule sources or reading dimensions
     */
    public boolean init() throws IOException {
        if (filterSlicers == null) {
            filterSlicers = Collections.emptyList();
            if (filter != null && reader instanceof StructuredGridCoverage2DReader) {
                StructuredGridCoverage2DReader structured = (StructuredGridCoverage2DReader) reader;
                String coverageName = coverageInfo.getNativeCoverageName();
                if (coverageName == null) {
                    coverageName = reader.getGridCoverageNames()[0];
                }
                List<String> dimensionAttributes = new ArrayList<>();

                // Extract Dimensions and related attributes
                List<DimensionDescriptor> dimensions = structured.getDimensionDescriptors(coverageName);
                Map<String, DimensionDescriptor> descriptors = new HashMap<>();
                for (DimensionDescriptor dim : dimensions) {
                    String attribute = dim.getStartAttribute();
                    dimensionAttributes.add(attribute);
                    descriptors.put(attribute, dim);
                }

                if (!dimensionAttributes.isEmpty()) {
                    // Extract filtered properties from the current filter,
                    // correlating them with the available dimensions
                    Set<String> filterProperties = new HashSet<>();
                    filter.accept(
                            new DefaultFilterVisitor() {
                                @Override
                                public Object visit(PropertyName expression, Object data) {
                                    filterProperties.add(expression.getPropertyName());
                                    return super.visit(expression, data);
                                }
                            },
                            null);

                    Set<String> normalizedDimensions = dimensionAttributes.stream()
                            .map(String::toLowerCase)
                            .collect(Collectors.toSet());

                    boolean involvesDimension =
                            filterProperties.stream().map(String::toLowerCase).anyMatch(normalizedDimensions::contains);

                    if (involvesDimension) {
                        filterSlicers = new ArrayList<>();
                        // Query on the granule source to return the unique values for each dimension
                        Set<String> groupable = new HashSet<>(dimensionAttributes);
                        groupable.retainAll(filterProperties);
                        Query query = new Query();
                        query.setFilter(filter);
                        query.setPropertyNames(groupable.toArray(new String[0]));
                        query.setMaxFeatures(1000);
                        GranuleSource granuleSource = structured.getGranules(coverageName, true);
                        SimpleFeatureCollection granules = granuleSource.getGranules(query);
                        for (String dimensionAttribute : dimensionAttributes) {
                            FilterSlicer filterSlicer = new FilterSlicer(descriptors.get(dimensionAttribute));
                            filterSlicers.add(filterSlicer);
                            filterSlicer.extractSlices(granules);
                        }
                    }
                }
            }
        }
        return !filterSlicers.isEmpty();
    }

    /**
     * Slices the original filter into multiple filters based on dimension combinations.
     *
     * This method breaks down a complex filter into a list of more specific filters,
     * removing range comparisons and generating all possible filter combinations
     * for the specified dimensions.
     *
     * @param original The original filter to be sliced
     * @return A list of filters representing different dimensional slices
     */
    public List<Filter> sliceFilter(Filter original) {
        Set<String> dimNames = filterSlicers.stream()
                .map(s -> s.propertyName)
                .collect(Collectors.toSet());

        Filter baseFilter = removeRangeComparisons(original, dimNames);

        // Generate all combinations of the dimensions
        List<Map<String, Object>> combinations = cartesianProduct(filterSlicers);
        List<Filter> output = new ArrayList<>();
        for (Map<String, Object> combo : combinations) {
            List<Filter> equals = combo.entrySet().stream()
                    .map(e -> e.getValue() == null
                            ? FF.isNull(FF.property(e.getKey()))
                            : FF.equals(FF.property(e.getKey()), FF.literal(e.getValue()))
                    ).collect(Collectors.toList());

            Filter combined;
            if (equals.isEmpty()) {
                combined = baseFilter;
            } else if (baseFilter == Filter.INCLUDE) {
                combined = FF.and(equals);
            } else {
                List<Filter> all = new ArrayList<>(equals.size() + 1);
                all.add(baseFilter);
                all.addAll(equals);
                combined = FF.and(all);
            }

            output.add(combined);
        }
        LOGGER.info("Splitting filtered request containing dimensions returned " + output.size() + " slices to be read");
        return output;
    }

    /**
     * Adds slice dimensions to a GridCoverage2D by updating its properties based on the given dimensions and filter.
     *
     * This method processes each dimension bean, retrieves the corresponding filter attribute,
     * and sets the coverage dimension properties.
     *
     * @param gridCoverage The original GridCoverage2D to be modified
     * @param dimensionBeans List of dimension beans to be applied
     * @param localFilter Filter to be used for dimension slicing
     * @return The GridCoverage2D with updated properties reflecting the slice dimensions values
     */
    public GridCoverage2D addSliceDimensions(GridCoverage2D gridCoverage, List<DimensionBean> dimensionBeans, Filter localFilter) {
        @SuppressWarnings("unchecked")
        Map<String, Object> map = gridCoverage.getProperties();
        if (map == null) {
            map = new HashMap<>();
        }
        for (DimensionBean coverageDimension : dimensionBeans) {
            String attribute = getFilterAttribute(coverageDimension, filterSlicers);
            setCoverageDimensionProperty(map, localFilter, coverageDimension, attribute);
        }
        // getProperties return a copy of the map. We need to recreate the coverage
        return GC_FACTORY.create(
                gridCoverage.getName(),
                gridCoverage.getRenderedImage(),
                gridCoverage.getEnvelope(),
                gridCoverage.getSampleDimensions(),
                null,
                map);
    }

    private String getFilterAttribute(DimensionBean coverageDimension, List<FilterSlicer> filterSlicers) {
        for (FilterSlicer extractor: filterSlicers) {
            if (coverageDimension.getName().equalsIgnoreCase(extractor.dimensionDescriptor.getName())) {
                return extractor.dimensionDescriptor.getStartAttribute();
            }
        }
        return null;
    }

    private Object parseValueByType(Object raw, DimensionBean.DimensionType type) {
        switch (type) {
            case TIME:
                Date date;
                if (raw instanceof Date) {
                    date = (Date) raw;
                } else if (raw instanceof java.sql.Timestamp) {
                    date = new Date(((Timestamp) raw).getTime());
                } else if (raw instanceof String) {
                    try {
                        date = javax.xml.bind.DatatypeConverter.parseDateTime((String) raw).getTime();
                    } catch (Exception e) {
                        throw new IllegalArgumentException("Unable to parse TIME dimension value: " + raw, e);
                    }
                } else {
                    throw new IllegalArgumentException("Unsupported TIME dimension value type: " + raw.getClass());
                }
                return new DateRange(date, date);

            case ELEVATION:
                Double value;
                if (raw instanceof Number) {
                    value = ((Number) raw).doubleValue();
                } else if (raw instanceof String) {
                    try {
                        value = Double.valueOf((String) raw);
                    } catch (NumberFormatException e) {
                        throw new IllegalArgumentException("Unable to parse ELEVATION dimension value: " + raw, e);
                    }
                } else {
                    throw new IllegalArgumentException("Unsupported ELEVATION dimension value type: " + raw.getClass());
                }
                return new NumberRange<>(Double.class, value, value);  // single-value range
        case CUSTOM:
            default:
                return raw;
        }
    }

    private void setCoverageDimensionProperty(
            Map<String, Object> properties, Filter filter, DimensionBean coverageDimension, String attribute) {
        Utilities.ensureNonNull("properties", properties);
        Utilities.ensureNonNull("coverageDimension", coverageDimension);

        final String dimName = coverageDimension.getName();
        final DimensionBean.DimensionType dimensionType = coverageDimension.getDimensionType();
        List<Object> matchedValues = new ArrayList<>();

        filter.accept(new DefaultFilterVisitor() {
            @Override
            public Object visit(PropertyIsEqualTo equal, Object data) {
                if (equal.getExpression1() instanceof PropertyName &&
                        ((PropertyName) equal.getExpression1()).getPropertyName().equalsIgnoreCase(attribute)) {

                    // Parse according to dimension type
                    Object raw = equal.getExpression2().evaluate(null);
                    Object parsed = parseValueByType(raw, dimensionType);

                    matchedValues.add(parsed);
                }
                return super.visit(equal, data);
            }
        }, null);

        if (matchedValues.isEmpty()) {
            throw new IllegalArgumentException("No value found in filter for dimension: " + dimName);
        }
        if (matchedValues.size() > 1) {
            throw new UnsupportedOperationException(
                    "Multiple values for dimension '" + dimName + "' are not supported on splitted requests");
        }

        properties.put(dimName, matchedValues.get(0));
    }

}

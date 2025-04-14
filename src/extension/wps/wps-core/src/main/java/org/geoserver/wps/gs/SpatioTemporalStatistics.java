/* (c) 2025 Open Source Geospatial Foundation - all rights reserved
  * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.wps.gs;

import it.geosolutions.jaiext.vectorbin.ROIGeometry;
import org.geoserver.catalog.Catalog;
import org.geoserver.catalog.CoverageInfo;
import org.geoserver.catalog.LayerInfo;
import org.geoserver.catalog.ResourcePool;
import org.geoserver.catalog.util.ReaderDimensionsAccessor;
import org.geotools.api.feature.simple.SimpleFeature;
import org.geotools.api.feature.simple.SimpleFeatureType;
import org.geotools.api.feature.type.AttributeDescriptor;
import org.geotools.api.feature.type.GeometryDescriptor;
import org.geotools.api.filter.FilterFactory;
import org.geotools.api.metadata.spatial.PixelOrientation;
import org.geotools.api.parameter.GeneralParameterValue;
import org.geotools.api.parameter.ParameterValue;
import org.geotools.api.parameter.ParameterValueGroup;
import org.geotools.api.referencing.crs.CoordinateReferenceSystem;
import org.geotools.api.referencing.operation.MathTransform;
import org.geotools.api.referencing.operation.TransformException;
import org.geotools.coverage.Category;
import org.geotools.coverage.GridSampleDimension;
import org.geotools.coverage.grid.GridCoverage2D;
import org.geotools.coverage.grid.io.AbstractGridFormat;
import org.geotools.coverage.grid.io.GridCoverage2DReader;
import org.geotools.coverage.processing.CoverageProcessor;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.factory.CommonFactoryFinder;
import org.geotools.feature.collection.DecoratingSimpleFeatureCollection;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.geometry.GeneralBounds;
import org.geotools.geometry.jts.JTS;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.process.ProcessException;
import org.geotools.process.factory.DescribeParameter;
import org.geotools.process.factory.DescribeProcess;
import org.geotools.process.factory.DescribeResult;
import org.geotools.process.factory.DescribeResults;
import org.geotools.referencing.CRS;
import org.geotools.referencing.operation.transform.ProjectiveTransform;
import org.geotools.util.DateRange;
import org.geotools.util.NumberRange;
import org.geotools.util.logging.Logging;
import org.jaitools.media.jai.zonalstats.ZonalStats;
import org.jaitools.media.jai.zonalstats.ZonalStatsDescriptor;
import org.jaitools.media.jai.zonalstats.ZonalStatsOpImage;
import org.jaitools.numeric.Range;
import org.jaitools.numeric.Statistic;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.util.AffineTransformation;
import org.locationtech.jts.simplify.DouglasPeuckerSimplifier;

import javax.media.jai.ROI;
import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Logger;

@DescribeProcess(
        title = "SpatioTemporal statistics",
        description = "Compute ")
public class SpatioTemporalStatistics implements GeoServerProcess {

    private static final CoverageProcessor PROCESSOR = CoverageProcessor.getInstance();

    public static final int MAX_TIME_ENTRIES = Integer.parseInt(
            System.getProperty("spatio.temporal.max.entries", "1000")
    );

    private static final EnumSet<Statistic> ALLOWED_STATS = EnumSet.of(
            Statistic.MIN, Statistic.MAX, Statistic.SUM, Statistic.MEAN, Statistic.MEDIAN
    );

    static final Logger LOGGER = Logging.getLogger(SpatioTemporalStatistics.class);

    private Catalog catalog;

    public SpatioTemporalStatistics(Catalog catalog) {
        this.catalog = catalog;
    }

    @DescribeResults({
        @DescribeResult(name = "result", description = "Georectified raster", type = GridCoverage2D.class),
        @DescribeResult(
                name = "path",
                description = "Pathname of the generated raster on the server",
                type = String.class)
    })

    public SimpleFeatureCollection execute(
            @DescribeParameter(name = "layerName", description = "Input layer name of a multi-temporal raster")
            String layerName,
            @DescribeParameter(name = "timeRange", description = "Time range over which the statistics should be computed")
            String timeRange,
            @DescribeParameter(name = "zones", description = "Zone polygon features for which to compute statistics")
            SimpleFeatureCollection zones,
            @DescribeParameter(name = "statsNames", description = "Comma separated list of requested statistics (min/max/sum/avg/median). Compute all if missing", min = 0)
            String statsNames)
            throws IOException {

        GridCoverage2DReader reader = null;
        Set<Statistic> requestedStats = parseStatistics(statsNames);
        DateRange dateRange = parseTimeRange(timeRange);
        LayerInfo layer = catalog.getLayerByName(layerName);
        if (layer == null) {
            throw new IOException("Layer '" + layerName + "' not found in catalog.");
        }

        // Validate and retrieve the coverage resource
        if (!(layer.getResource() instanceof CoverageInfo)) {
            throw new IOException("Layer '" + layerName + "' is not a coverage resource.");
        }
        CoverageInfo coverage = (CoverageInfo) layer.getResource();

        // Obtain a reader from the coverage
        try {
            reader = (GridCoverage2DReader) coverage.getGridCoverageReader(null, null);
            if (reader == null) {
                throw new IOException("Unable to obtain a reader for layer: " + layerName);
            }

            // Use the dimensions accessor to extract time values within the given date range.
            ReaderDimensionsAccessor dimensionsAccessor = new ReaderDimensionsAccessor(reader);

            TreeSet<Object> timeDomain = dimensionsAccessor.getTimeDomain(dateRange, MAX_TIME_ENTRIES);
            return new SpatioTemporalZonalStatisticsCollection(reader, zones, timeDomain, requestedStats);
        } finally {
            if (reader != null) {
                reader.dispose();
            }
        }

   }

    static class StatisticsAggregator {

        private Set<Statistic> requestedStats;
        private double aggregatedSum;
        private double aggregatedAvg;
        private double aggregatedMin = Double.POSITIVE_INFINITY;
        private double aggregatedMax = Double.NEGATIVE_INFINITY;
        private List<Double> medians = new ArrayList<>();
        //private int count;
        private int aggregated;

        public StatisticsAggregator(Set<Statistic> requestedStats) {
            this.requestedStats = requestedStats;
        }

        /**
         * Aggregates a new CoverageStatistics instance into the running totals.
         * Assumes that all coverages are weighted equally.
         *
         * @param stats the statistics for one coverage reading.
         */
        public void addCoverageStats(ZonalStats stats) {
            if (stats == null) {
                return;
            }
            aggregated++;

            if (requestedStats.contains(Statistic.SUM)) {
                this.aggregatedSum += getStatsValue(stats, Statistic.SUM);
            }

            if (requestedStats.contains(Statistic.MEAN)) {
                this.aggregatedAvg = this.aggregatedAvg + (getStatsValue(stats, Statistic.MEAN) - this.aggregatedAvg) / aggregated;
            }

            if (requestedStats.contains(Statistic.MIN)) {
                this.aggregatedMin = Math.min(this.aggregatedMin, getStatsValue(stats, Statistic.MIN));
            }

            if (requestedStats.contains(Statistic.MAX)) {
                this.aggregatedMax = Math.max(this.aggregatedMax, getStatsValue(stats, Statistic.MAX));
            }

            if (requestedStats.contains(Statistic.MEDIAN)) {
                medians.add(getStatsValue(stats, Statistic.MEDIAN));
            }

        }

        public ZonalStats aggregate() {
            ZonalStats zs = new ZonalStats
        }
        public double getAggregatedSum() {
            return aggregatedSum;
        }

        public double getAggregatedAvg() {
            return aggregatedAvg;
        }

        public double getAggregatedMin() {
            return aggregatedMin;
        }

        public double getAggregatedMax() {
            return aggregatedMax;
        }

        /**
         * Returns the median of the medians collected.
         * Note: The true aggregate median may require access to the original raw data.
         *
         * @return the approximate aggregated median.
         */
        public double getAggregatedMedian() {
            if (medians.isEmpty()) return Double.NaN;
            Collections.sort(medians);
            int size = medians.size();
            if (size % 2 == 1) {
                return medians.get(size / 2);
            } else {
                return (medians.get(size / 2 - 1) + medians.get(size / 2)) / 2.0;
            }
        }

        /**
         * Returns the number of coverages aggregated.
         *
         * @return count of coverages.
         */
        public int getAggregated() {
            return aggregated;
        }
    }


    /**
     * A feature collection that computes zonal statitics in a streaming fashion
     *
     * @author Andrea Aime - OpenGeo
     */
    static class SpatioTemporalZonalStatisticsCollection extends DecoratingSimpleFeatureCollection {

        GridCoverage2DReader reader;
        TreeSet<Object> times;
        SimpleFeatureType targetSchema;

        Set<Statistic> requestedStats;

        public SpatioTemporalZonalStatisticsCollection(
                GridCoverage2DReader reader, SimpleFeatureCollection zones, TreeSet<Object> times, Set<Statistic> requestedStats) {
            super(zones);
            this.reader = reader;
            this.times = times;
            this.requestedStats = requestedStats;

            SimpleFeatureTypeBuilder tb = new SimpleFeatureTypeBuilder();
            for (AttributeDescriptor att : zones.getSchema().getAttributeDescriptors()) {
                tb.minOccurs(att.getMinOccurs());
                tb.maxOccurs(att.getMaxOccurs());
                tb.restrictions(att.getType().getRestrictions());
                if (att instanceof GeometryDescriptor) {
                    GeometryDescriptor gatt = (GeometryDescriptor) att;
                    tb.crs(gatt.getCoordinateReferenceSystem());
                }
                tb.add("z_" + att.getLocalName(), att.getType().getBinding());
            }

            tb.add("count", Long.class);
            addRequestedStatisticsAttributes(tb, requestedStats);
            tb.setName(zones.getSchema().getName());
            targetSchema = tb.buildFeatureType();
        }

        @Override
        public SimpleFeatureType getSchema() {
            return targetSchema;
        }

        @Override
        public SimpleFeatureIterator features() {
            return new SpatioTemporalZonalStatisticsIterator(delegate.features(), reader, targetSchema, times, requestedStats);
        }
    }


    static class SpatioTemporalZonalStatisticsIterator implements SimpleFeatureIterator {

        private final TreeSet<Object> times;
        FilterFactory ff = CommonFactoryFinder.getFilterFactory(null);

        GridCoverage2DReader reader;

        SimpleFeatureIterator zones;

        SimpleFeatureBuilder builder;

        List<SimpleFeature> features = new ArrayList<>();

        Set<Statistic> requestedStats;

        public SpatioTemporalZonalStatisticsIterator(
                SimpleFeatureIterator zones,
                GridCoverage2DReader reader,
                SimpleFeatureType targetSchema,
                TreeSet<Object> times,
                Set<Statistic> requestedStats) {
            this.zones = zones;
            this.builder = new SimpleFeatureBuilder(targetSchema);
            this.reader = reader;
            this.requestedStats = requestedStats;
            this.times = times;

        }

        @Override
        public void close() {
            zones.close();
        }

        @Override
        public boolean hasNext() {
            return !features.isEmpty() || zones.hasNext();
        }

        @Override
        public SimpleFeature next() throws NoSuchElementException {
            // build the next set of features if necessary
            if (features.isEmpty()) {
                // grab the current zone
                SimpleFeature zone = zones.next();

                try {
                    // grab the geometry and eventually reproject it to the
                    Geometry zoneGeom = (Geometry) zone.getDefaultGeometry();
                    CoordinateReferenceSystem dataCrs = reader.getCoordinateReferenceSystem();
                    CoordinateReferenceSystem zonesCrs =
                            builder.getFeatureType().getGeometryDescriptor().getCoordinateReferenceSystem();
                    if (!CRS.equalsIgnoreMetadata(zonesCrs, dataCrs)) {
                        zoneGeom = JTS.transform(zoneGeom, CRS.findMathTransform(zonesCrs, dataCrs, true));
                    }

                    // gather the statistics
                    ZonalStats stats = processStatistics(zoneGeom);

                    // build the resulting feature
                    if (stats != null) {
                        builder.addAll(zone.getAttributes());
                        addStatsToFeature(stats, requestedStats);
                        features.add(builder.buildFeature(zone.getID()));

                    } else {
                        builder.addAll(zone.getAttributes());
                        features.add(builder.buildFeature(zone.getID()));
                    }
                } catch (Exception e) {
                    throw new ProcessException("Failed to compute statistics on feature " + zone, e);
                }
            }
            // return the first feature in the current buffer
            SimpleFeature f = features.remove(0);
            return f;
        }

        /**
         * Add the statistics to the feature builder
         */
        void addStatsToFeature(ZonalStats stats, Set<Statistic> requestedStats) {

            double count = stats.statistic(Statistic.MEAN).results().get(0).getNumAccepted();
            builder.add(count); // count
            addDynamicStatsToFeature(builder, stats, requestedStats);
        }

        private void addDynamicStatsToFeature(SimpleFeatureBuilder builder, ZonalStats stats, Set<Statistic> requestedStats) {
            if (requestedStats.contains(Statistic.MIN)) {
                builder.add(getStatsValue(stats, Statistic.MIN));
            }
            if (requestedStats.contains(Statistic.MAX)) {
                builder.add(getStatsValue(stats, Statistic.MAX));
            }
            if (requestedStats.contains(Statistic.SUM)) {
                builder.add(getStatsValue(stats, Statistic.SUM));
            }
            if (requestedStats.contains(Statistic.MEAN)) {
                builder.add(getStatsValue(stats, Statistic.MEAN));
            }
            if (requestedStats.contains(Statistic.MEDIAN)) {
                builder.add(getStatsValue(stats, Statistic.MEDIAN));
            }
        }

        // This code has been imported from GT RasterZonalStatistics
        private ZonalStats processStatistics(Geometry geometry) throws TransformException, IOException {
            GridCoverage2D refCoverage = null;
            GridCoverage2D dataCoverage = null;
            GridCoverage2D cropped = null;
            ReferencedEnvelope geometryEnvelope = null;
            Geometry simplifiedGeometry = null;
            List<Range<Double>> novalueRangeList = null;

            MathTransform w2gTransform = null;

            ParameterValue<List> timeParam = AbstractGridFormat.TIME.createValue();
            Statistic[] reqStatsArr = requestedStats.toArray(new Statistic[0]);
            StatisticsAggregator aggregator = new StatisticsAggregator(requestedStats);

            for (Object temporalItem: times) {
                // Let's either support Date only or DateRange only, not mixed
                if (temporalItem instanceof Date) {
                    Date time = (Date) temporalItem;
                    timeParam.setValue(Collections.singleton(time));
                } else if (temporalItem instanceof DateRange) {
                    DateRange range = (DateRange) temporalItem;
                    timeParam.setValue(Collections.singleton(range));
                }
                GeneralParameterValue[] gpv = new GeneralParameterValue[]{timeParam};
                try {
                    dataCoverage = reader.read(gpv);
                    // Assume we can share the same bbox/crop to all the times
                    if (refCoverage == null) {
                        refCoverage = dataCoverage;

                        final AffineTransform dataG2WCorrected = new AffineTransform(
                                (AffineTransform) dataCoverage.getGridGeometry().getGridToCRS2D(PixelOrientation.UPPER_LEFT));
                        try {
                            w2gTransform = ProjectiveTransform.create(dataG2WCorrected.createInverse());
                        } catch (NoninvertibleTransformException e) {
                            throw new IllegalArgumentException(e.getLocalizedMessage());
                        }


                        // first off, cut the geometry around the coverage bounds if necessary
                        ReferencedEnvelope coverageEnvelope = new ReferencedEnvelope(dataCoverage.getEnvelope2D());
                        geometryEnvelope = new ReferencedEnvelope(
                                geometry.getEnvelopeInternal(), dataCoverage.getCoordinateReferenceSystem());
                        if (!coverageEnvelope.intersects((Envelope) geometryEnvelope)) {
                            // no intersection, no stats
                            return null;
                        } else if (!coverageEnvelope.contains((Envelope) geometryEnvelope)) {
                            // the geometry goes outside of the coverage envelope, that makes
                            // the stats fail for some reason
                            geometry = JTS.toGeometry((Envelope) coverageEnvelope).intersection(geometry);
                            geometryEnvelope = new ReferencedEnvelope(
                                    geometry.getEnvelopeInternal(), dataCoverage.getCoordinateReferenceSystem());
                        }

                        // check if the novalue is != from NaN
                        GridSampleDimension sampleDimension = dataCoverage.getSampleDimension(0);
                        List<Category> categories = sampleDimension.getCategories();
                        if (categories != null) {
                            for (Category category : categories) {
                                String catName = category.getName().toString();
                                if (catName.equalsIgnoreCase("no data")) {
                                    NumberRange range = category.getRange();
                                    double min = range.getMinimum();
                                    double max = category.getRange().getMaximum();
                                    if (!Double.isNaN(min) && !Double.isNaN(max)) {
                                        // we have to filter those out
                                        Range<Double> novalueRange = new Range<>(min, true, max, true);
                                        novalueRangeList = new ArrayList<>();
                                        novalueRangeList.add(novalueRange);
                                    }
                                    break;
                                }
                            }
                        }
                        // transform the geometry to raster space so that we can use it as a ROI source
                        Geometry rasterSpaceGeometry = JTS.transform(geometry, w2gTransform);

                        // simplify the geometry so that it's as precise as the coverage, excess coordinates
                        // just make it slower to determine the point in polygon relationship
                        simplifiedGeometry = DouglasPeuckerSimplifier.simplify(rasterSpaceGeometry, 1);

                        // compensate for the jaitools range lookup poking the corner of the cells instead
                        // of their center, this makes for odd results if the polygon is just slightly
                        // misaligned with the coverage
                        AffineTransformation at = new AffineTransformation();

                        at.setToTranslation(-0.5, -0.5);
                        simplifiedGeometry.apply(at);

                    }
                    /*
                     * crop on region of interest
                     */
                    ParameterValueGroup param =
                            PROCESSOR.getOperation("CoverageCrop").getParameters();
                    param.parameter("Source").setValue(dataCoverage);
                    param.parameter("Envelope").setValue(new GeneralBounds(geometryEnvelope));
                    cropped = (GridCoverage2D) PROCESSOR.doOperation(param);

                    // build a shape using a fast point in polygon wrapper
                    ROI roi = new ROIGeometry(simplifiedGeometry, false);

                    final ZonalStatsOpImage zsOp = new ZonalStatsOpImage(
                            cropped.getRenderedImage(),
                            null,
                            null,
                            null,
                            reqStatsArr,
                            null,
                            roi,
                            null,
                            null,
                            null,
                            false,
                            novalueRangeList);
                    aggregator.addCoverageStats((ZonalStats) zsOp.getProperty(ZonalStatsDescriptor.ZONAL_STATS_PROPERTY));
                } finally {
                    // dispose coverages
                    if (cropped != null) {
                        cropped.dispose(true);
                    }
                    if (dataCoverage != null) {
                        dataCoverage.dispose(true);
                    }
                }
            }
        }
        return
    }

    private static double getStatsValue(ZonalStats zonalStats, Statistic statistic) {
        return zonalStats.statistic(statistic).results().get(0).getValue();
    }

    private static void addRequestedStatisticsAttributes(SimpleFeatureTypeBuilder tb, Set<Statistic> requestedStats) {
        tb.add("count", Long.class); // count is always added

        if (requestedStats.contains(Statistic.MIN)) {
            tb.add("min", Double.class);
        }
        if (requestedStats.contains(Statistic.MAX)) {
            tb.add("max", Double.class);
        }
        if (requestedStats.contains(Statistic.SUM)) {
            tb.add("sum", Double.class);
        }
        if (requestedStats.contains(Statistic.MEAN)) {
            tb.add("avg", Double.class);
        }
        if (requestedStats.contains(Statistic.MEDIAN)) {
            tb.add("median", Double.class);
        }
    }


    private static Set<Statistic> parseStatistics(String statsNames) throws IOException {
        Set<Statistic> requestedStats = new HashSet<>();

        if (statsNames == null || statsNames.trim().isEmpty()) {
            requestedStats.addAll(ALLOWED_STATS);
            return requestedStats;
        }

        String[] tokens = statsNames.split(",");
        for (String token : tokens) {
            String statName = token.trim().toUpperCase();
            try {
                Statistic stat = Statistic.valueOf(statName);
                if (!ALLOWED_STATS.contains(stat)) {
                    throw new IOException("Statistic not allowed: " + statName);
                }
                requestedStats.add(stat);
            } catch (IllegalArgumentException e) {
                throw new IOException("Unknown statistic: " + statName, e);
            }
        }

        return requestedStats;
    }

    private static DateRange parseTimeRange(String timeRange) throws IllegalArgumentException {
        if (timeRange == null || timeRange.trim().isEmpty()) {
            throw new IllegalArgumentException("Time range string cannot be null or empty");
        }

        // Expect a simple "start/end" split.
        String[] parts = timeRange.split("/");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid ISO8601 time range format: " + timeRange);
        }

        Date startDate = null;
        Date endDate = null;

        // Parse the start time if provided
        if (!parts[0].trim().isEmpty()) {
            try {
                Instant startInstant = Instant.parse(parts[0].trim());
                startDate = Date.from(startInstant);
            } catch (Exception ex) {
                throw new IllegalArgumentException("Invalid start date: " + parts[0].trim());
            }
        }

        // Parse the end time if provided
        if (!parts[1].trim().isEmpty()) {
            try {
                Instant endInstant = Instant.parse(parts[1].trim());
                endDate = Date.from(endInstant);
            } catch (Exception ex) {
                throw new IllegalArgumentException("Invalid end datee: " + parts[1].trim());
            }
        }

        // Construct the DateRange from the parsed dates.
        return new DateRange(startDate, endDate);
    }

}

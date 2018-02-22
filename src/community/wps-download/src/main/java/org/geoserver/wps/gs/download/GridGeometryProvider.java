/* (c) 2018 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.wps.gs.download;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.geotools.coverage.grid.GridGeometry2D;
import org.geotools.coverage.grid.io.DimensionDescriptor;
import org.geotools.coverage.grid.io.GranuleSource;
import org.geotools.coverage.grid.io.GridCoverage2DReader;
import org.geotools.coverage.grid.io.StructuredGridCoverage2DReader;
import org.geotools.data.Query;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.referencing.CRS;
import org.geotools.resources.coverage.FeatureUtilities;
import org.geotools.util.logging.Logging;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.GeometryDescriptor;
import org.opengis.filter.Filter;
import org.opengis.filter.expression.PropertyName;
import org.opengis.geometry.BoundingBox;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.NoSuchAuthorityCodeException;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.operation.MathTransform;
import org.opengis.referencing.operation.TransformException;

/**
 * A class delegated to return the proper GridGeometry to be used by a raster Download
 * when target size is not specified. 
 */
class GridGeometryProvider {

    private static final Logger LOGGER = Logging.getLogger(GridGeometryProvider.class);
    
    /**
     * A trivial CRS cache in order to avoid several CRS.decode for the same EPSG 
     */
    class CRSCache {
        Map<String, CoordinateReferenceSystem> cache = new HashMap<String, CoordinateReferenceSystem>();
        
        public CoordinateReferenceSystem getCrs(String crsId) throws NoSuchAuthorityCodeException, FactoryException {
            CoordinateReferenceSystem crs = null;
            if (!cache.containsKey(crsId)) {
                crs = CRS.decode(crsId);
                cache.put(crsId, crs);
            } else {
                crs = cache.get(crsId);    
            }
            return crs;
        }
    }
    
    /**
     * Provide the best resolution  
     */
    class ResolutionProvider {
        
        private DimensionDescriptor resDescriptor;

        private DimensionDescriptor resXDescriptor;

        private DimensionDescriptor resYDescriptor;

        private DimensionDescriptor crsDescriptor;

        private boolean hasBothResolutions;

        private boolean isHeterogeneousCrs;
        
        private CRSCache crsCache; 
        
        public ResolutionProvider(Map<String, DimensionDescriptor> descriptors) {
            resDescriptor = descriptors.get(DimensionDescriptor.RESOLUTION);
            resXDescriptor = descriptors.get(DimensionDescriptor.RESOLUTION_X);
            resYDescriptor = descriptors.get(DimensionDescriptor.RESOLUTION_Y);
            crsDescriptor = descriptors.get(DimensionDescriptor.CRS);
            hasBothResolutions = resXDescriptor != null && resYDescriptor != null;
            isHeterogeneousCrs = crsDescriptor != null;
            crsCache = new CRSCache();
        }

        /**
         * No resolution can be provided if there isn't any resolution related descriptor
         */
        boolean isAvailable() {
            return resDescriptor != null || (resXDescriptor != null && resYDescriptor != null);
        }

        /**
         * Get the best resolution from the input {@link SimpleFeatureCollection}.
         * @param features
         * @return
         * @throws NoSuchAuthorityCodeException
         * @throws FactoryException
         * @throws TransformException
         */
        public double[] getResolution(SimpleFeatureCollection features)
                throws NoSuchAuthorityCodeException, FactoryException, TransformException {
            
            // Setting up features attributes to be checked
            final String resXAttribute = hasBothResolutions ? resXDescriptor.getStartAttribute()
                    : resDescriptor.getStartAttribute();
            final String resYAttribute = hasBothResolutions ? resYDescriptor.getStartAttribute()
                    : resDescriptor.getStartAttribute();

            String crsAttribute = null;
            CoordinateReferenceSystem schemaCrs = null;

            if (isHeterogeneousCrs) {
                // We need special management in that case
                SimpleFeatureType schema = features.getSchema();
                schemaCrs = schema.getGeometryDescriptor().getCoordinateReferenceSystem();
                crsAttribute = crsDescriptor.getStartAttribute();
            }

            // Initialize bestResolution with infinite numbers
            double[] bestResolution = new double[] {Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY};

            // Iterate over the features to extract the best resolution
            try (SimpleFeatureIterator iterator = features.features()) {
                double[] featureResolution = new double[2];
                while (iterator.hasNext()) {
                    extractResolution(iterator.next(), resXAttribute, resYAttribute, crsAttribute,
                            schemaCrs, featureResolution);
                    for (int i = 0; i < 2; i++) {
                        // Simple iteration over X and Y components
                        bestResolution[i] = featureResolution[i] < bestResolution[i]
                                ? featureResolution[i] : bestResolution[i];
                    }
                    
                }
            }
            return bestResolution;
        }

        /**
         * 
         * @param feature
         * @param resXAttribute
         * @param resYAttribute
         * @param crsAttribute
         * @param schemaCrs
         * @param updatedResolution
         * @throws NoSuchAuthorityCodeException
         * @throws FactoryException
         * @throws TransformException
         */
        private void extractResolution(SimpleFeature feature, String resXAttribute,
                String resYAttribute, String crsAttribute, CoordinateReferenceSystem schemaCrs,
                double[] updatedResolution) throws NoSuchAuthorityCodeException, FactoryException, TransformException {
            updatedResolution[0] = (Double) feature.getAttribute(resXAttribute);
            updatedResolution[1] = hasBothResolutions ? (Double) feature.getAttribute(resYAttribute) : updatedResolution[0];
            if (isHeterogeneousCrs) {
                String crsId = (String) feature.getAttribute(crsAttribute);
                CoordinateReferenceSystem granuleCrs = crsCache.getCrs(crsId);
                transformResolution(feature, schemaCrs, granuleCrs, updatedResolution);
            }            
        }

        /**
         * Compute the transformed resolution of the provided feature since we are in the case of
         * heterogeneous CRS.

         * @param feature
         * @param schemaCrs
         * @param granuleCrs
         * @param localRes
         * @throws FactoryException
         * @throws TransformException
         */
        private void transformResolution(SimpleFeature feature, CoordinateReferenceSystem schemaCrs, 
                CoordinateReferenceSystem granuleCrs, double[] localRes) throws FactoryException, TransformException {
            MathTransform transform = CRS.findMathTransform(schemaCrs, granuleCrs);
            
            if (!transform.isIdentity()) {
                BoundingBox bounds = feature.getBounds();
                MathTransform inverse = transform.inverse();
                
                // Get the center coordinate in the granule's CRS
                double center[] = new double[] {
                        (bounds.getMaxX() + bounds.getMinX()) / 2, 
                        (bounds.getMaxY() + bounds.getMinY()) / 2};
                transform.transform(center, 0, center, 0, 1);    
                
                // Setup 2 segments in granule's CRS
                double [] coords = new double[6];
                double [] resCoords = new double[6];

                // center
                coords[0] = center[0];
                coords[1] = center[1];
                
                // DX from center
                coords[2] = center[0] + localRes[0];
                coords[3] = center[1];
                
                // DY from center
                coords[4] = center[0];
                coords[5] = center[1] + localRes[1];

                // Transform the coordinates back to schemaCrs
                inverse.transform(coords, 0, resCoords, 0, 3);
                
                double dx1 = coords[2] - coords[0];
                double dx2 = coords[3] - coords[1];
                double dy1 = coords[4] - coords[0];
                double dy2 = coords[5] - coords[1];

                // Computing euclidean distances
                double transformedDX = Math.sqrt(dx1 * dx1 + dx2 * dx2);
                double transformedDY = Math.sqrt(dy1 * dy1 + dy2 * dy2);
                localRes[0] = transformedDX;
                localRes[1] = transformedDY;
            }
        }
    }

    private GridCoverage2DReader reader;

    private ROIManager roiManager;

    private Filter filter;

    public GridGeometryProvider(GridCoverage2DReader reader, ROIManager roiManager, Filter filter) {
        this.reader = reader;
        this.roiManager = roiManager;
        this.filter = filter;
    }

    public GridGeometry2D getGridGeometry()
            throws TransformException, IOException, FactoryException {
        if (!StructuredGridCoverage2DReader.class.isAssignableFrom(reader.getClass())) {

            //
            // CASE A: simple readers: return the native resolution gridGeometry
            //
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.fine("The underlying reader is not structured; returning native resolution");
                return getNativeResolutionGridGeometry();
            }
            return getNativeResolutionGridGeometry();
        } else {
            //
            // CASE B: StructuredGridCoverage2DReader
            // 

            StructuredGridCoverage2DReader structuredReader = (StructuredGridCoverage2DReader) reader;
            String coverageName = reader.getGridCoverageNames()[0];

            Map<String, DimensionDescriptor> descriptors = structuredReader
                    .getDimensionDescriptors(coverageName).stream()
                    .collect(Collectors.toMap(dd -> dd.getName(), dd -> dd));

            ResolutionProvider resProvider = new ResolutionProvider(descriptors);

            //
            // Do we have any resolution descriptor available?
            // if not, go to standard computation.
            //
            if (resProvider.isAvailable()) {
                if (LOGGER.isLoggable(Level.FINE)) {
                    LOGGER.fine("The underlying reader is structured but no resolution domains are available; "
                            + "returning native resolution");
                }
                return getNativeResolutionGridGeometry();
            }

            // Proceed by querying the resolution
            GranuleSource granules = structuredReader.getGranules(coverageName, true);

            // get the query on top of roi and input filter (if any)
            Query query = initQuery(granules);
            SimpleFeatureCollection features = granules.getGranules(query);

            if (features == null || features.isEmpty()) {
                if (LOGGER.isLoggable(Level.FINE)) {
                    LOGGER.fine("No features available for the specified query, returning native resolution");
                    return getNativeResolutionGridGeometry();
                }
            }
            double[] resolutions = resProvider.getResolution(features);
            return null;
        }
    }

    /**
     * Initialize the query to get based on provided filter and region of interest (if any)
     * @param granules
     * @return
     * @throws TransformException
     * @throws FactoryException
     * @throws IOException
     */
    private Query initQuery(GranuleSource granules)
            throws TransformException, FactoryException, IOException {
        List<Filter> filters = new ArrayList<Filter>();

        Query query = Query.ALL;

        // Set bbox query if a ROI has been provided
        if (roiManager != null) {
            CoordinateReferenceSystem targetCRS = roiManager.getTargetCRS();
            ReferencedEnvelope envelope = new ReferencedEnvelope(
                    roiManager.getSafeRoiInTargetCRS().getEnvelopeInternal(), targetCRS);
            GeometryDescriptor geomDescriptor = granules.getSchema().getGeometryDescriptor();
            CoordinateReferenceSystem indexCRS = geomDescriptor.getCoordinateReferenceSystem();
            MathTransform reprojectionTrasform = null;
            if (!CRS.equalsIgnoreMetadata(targetCRS, indexCRS)) {
                reprojectionTrasform = CRS.findMathTransform(targetCRS, indexCRS, true);
                if (!reprojectionTrasform.isIdentity()) {
                    envelope = envelope.transform(indexCRS, true);
                }
            }
            final PropertyName geometryProperty = FeatureUtilities.DEFAULT_FILTER_FACTORY
                    .property(geomDescriptor.getName());
            filters.add(FeatureUtilities.DEFAULT_FILTER_FACTORY.bbox(geometryProperty, envelope));
        }

        // check an input filter being specified
        if (filter != null) {
            filters.add(filter);
        }

        // Set the query filter
        if (!filters.isEmpty()) {
            query = new Query();
            query.setFilter(FeatureUtilities.DEFAULT_FILTER_FACTORY.and(filters));
        }
        return query;
    }

    /**
     * Default GridGeometry retrieval based on native resolution.
     * 
     * @return
     * @throws TransformException
     * @throws IOException
     */
    private GridGeometry2D getNativeResolutionGridGeometry()
            throws TransformException, IOException {
        final ReferencedEnvelope roiEnvelope = roiManager != null
                ? new ReferencedEnvelope(roiManager.getSafeRoiInNativeCRS().getEnvelopeInternal(),
                        roiManager.getNativeCRS()) : null;
        ScaleToTarget scaling = new ScaleToTarget(reader, roiEnvelope);
        scaling.setTargetSize(null, null);
        return scaling.getGridGeometry();
    }

}

/* (c) 2024 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.mapml;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import org.geoserver.catalog.LayerInfo;
import org.geoserver.catalog.ResourceInfo;
import org.geoserver.catalog.SLDHandler;
import org.geoserver.catalog.StoreInfo;
import org.geoserver.catalog.WMSStoreInfo;
import org.geoserver.catalog.WMTSStoreInfo;
import org.geoserver.mapml.tcrs.Point;
import org.geoserver.mapml.tcrs.TiledCRSConstants;
import org.geoserver.mapml.tcrs.TiledCRSParams;
import org.geoserver.ows.URLMangler;
import org.geoserver.ows.util.ResponseUtils;
import org.geoserver.platform.GeoServerExtensions;
import org.geoserver.security.CoverageAccessLimits;
import org.geoserver.security.DataAccessLimits;
import org.geoserver.security.ResourceAccessManager;
import org.geoserver.security.VectorAccessLimits;
import org.geoserver.security.WMSAccessLimits;
import org.geoserver.security.WMTSAccessLimits;
import org.geoserver.wms.GetMapRequest;
import org.geoserver.wms.WMSMapContent;
import org.geotools.api.filter.Filter;
import org.geotools.api.referencing.FactoryException;
import org.geotools.ows.wms.Layer;
import org.geotools.ows.wms.WMSCapabilities;
import org.geotools.ows.wmts.model.TileMatrix;
import org.geotools.ows.wmts.model.TileMatrixSet;
import org.geotools.ows.wmts.model.TileMatrixSetLink;
import org.geotools.ows.wmts.model.WMTSCapabilities;
import org.geotools.ows.wmts.model.WMTSLayer;
import org.geotools.referencing.CRS;
import org.geotools.util.logging.Logging;
import org.locationtech.jts.geom.Geometry;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;

/**
 * This class takes care of setting up URL Template, including preparing cascaded URL when a
 * cascaded Layer is configured to useRemote service, in case the request satisfies criteria to go
 * directly through the remote service.
 */
public class MapMLRequestMangler {

    private static final String WMTS = "WMTS";
    private static final String REQUEST = "request";
    private static final String LAYER = "layer";
    private static final String GETMAP = "GETMAP";
    private static final String GETFEATUREINFO = "GETFEATUREINFO";
    private static final String WMS = "WMS";
    private static final String GETTILE = "GETTILE";
    private static final String SERVICE = "service";
    private static final String LAYERS = "layers";
    private static final String CRS_PARAM = "crs";
    private static final String SRS_PARAM = "srs";
    private static final String WMS_1_3_0 = "1.3.0";

    private static final double ORIGIN_DELTA = 0.1;
    private static final double SCALE_DELTA = 1E-5;

    static class CRSMapper {

        Set<String> inputCRSs;

        String outputCRS;

        public CRSMapper(Set<String> inputCRSs, String outputCRS) {
            this.inputCRSs = inputCRSs;
            this.outputCRS = outputCRS;
        }

        boolean isSupporting(String inputCRS) {
            return inputCRSs.contains(inputCRS);
        }

        String getOutputCRS() {
            return outputCRS;
        }
    }

    private static final Set<CRSMapper> crsMappers;

    static {
        crsMappers = new HashSet<>();
        crsMappers.add(
                new CRSMapper(
                        Set.of(
                                "EPSG:4326",
                                "urn:ogc:def:crs:EPSG::4326",
                                "urn:ogc:def:crs:MapML::WGS84"),
                        "EPSG:4326"));
        crsMappers.add(
                new CRSMapper(
                        Set.of("EPSG:3857", "urn:ogc:def:crs:EPSG::3857", "MapML:OSMTILE"),
                        "EPSG:3857"));
        crsMappers.add(
                new CRSMapper(
                        Set.of("EPSG:5936", "urn:ogc:def:crs:EPSG::5936", "MapML:APSTILE"),
                        "EPSG:5936"));
        crsMappers.add(
                new CRSMapper(
                        Set.of("EPSG:3978", "urn:ogc:def:crs:EPSG::3978", "MapML:CBMTILE"),
                        "EPSG:3978"));
    }

    private static final Logger LOGGER = Logging.getLogger(MapMLRequestMangler.class);
    private final MapMLDocumentBuilder.MapMLLayerMetadata mapMLLayerMetadata;
    private final String path;
    private final String baseUrlPattern;
    private final String proj;
    private final HashMap<String, String> params;
    private final WMSMapContent mapContent;
    private ResourceAccessManager resourceAccessManager;

    public MapMLRequestMangler(
            WMSMapContent mapContent,
            MapMLDocumentBuilder.MapMLLayerMetadata mapMLLayerMetadata,
            String baseUrlPattern,
            String path,
            HashMap<String, String> params,
            String proj) {
        this.mapContent = mapContent;
        this.mapMLLayerMetadata = mapMLLayerMetadata;
        this.path = path;
        this.params = params;
        this.baseUrlPattern = baseUrlPattern;
        this.proj = proj;
        List<ResourceAccessManager> resourceAccessManagerList =
                GeoServerExtensions.extensions(ResourceAccessManager.class);
        // In theory it will always be just a single bean
        // Using extensions instead of bean For testing purposes, let's use the latest
        // That's not optimal but I didn't find a faster approach to test DataAccessLimits.
        this.resourceAccessManager =
                resourceAccessManagerList.get(resourceAccessManagerList.size() - 1);
    }

    public String getUrlTemplate() {
        LayerInfo layerInfo = mapMLLayerMetadata.getLayerInfo();
        String urlTemplate = "";
        try {
            if (!canCascade(layerInfo)) {
                urlTemplate =
                        URLDecoder.decode(
                                ResponseUtils.buildURL(
                                        baseUrlPattern, path, params, URLMangler.URLType.SERVICE),
                                "UTF-8");
            } else {
                urlTemplate = tryCascading(path, params, layerInfo);
            }
        } catch (UnsupportedEncodingException uee) {
        }
        return urlTemplate;
    }

    /**
     * Check metadata, request Params and layerInfo configuration to verify if there are minimal
     * requirements for a potential cascading to the remote service.
     */
    private boolean canCascade(LayerInfo layerInfo) {
        if (mapMLLayerMetadata.isUseRemote()) {
            if (hasRestrictingAccessLimits(layerInfo)) return false;
            if (hasVendorParams()) return false;
            ResourceInfo resource = layerInfo.getResource();
            StoreInfo storeInfo = resource.getStore();
            // Not supporting cross-requests yet:
            // GetTiles against remote WMS
            // GetMap against remote WMTS
            String service = params.get(SERVICE);
            String request = params.get(REQUEST);
            if (storeInfo instanceof WMTSStoreInfo) {
                if (GETMAP.equalsIgnoreCase(request)
                        || (GETFEATUREINFO.equalsIgnoreCase(request)
                                && WMS.equalsIgnoreCase(service))) {
                    return false;
                }
            } else if (storeInfo instanceof WMSStoreInfo) {
                if (GETTILE.equalsIgnoreCase(request)
                        || (GETFEATUREINFO.equalsIgnoreCase(request)
                                && WMTS.equalsIgnoreCase(service))) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }
    /**
     * Try cascading to the remote Server unless any condition is preventing that (i.e. CRS not
     * supported on the remote server)
     */
    private String tryCascading(String path, HashMap<String, String> params, LayerInfo layerInfo)
            throws UnsupportedEncodingException {
        String baseUrl = baseUrlPattern;
        String version = "1.3.0";
        String reason = null;
        boolean doCascade = false;
        URLMangler.URLType urlType = URLMangler.URLType.SERVICE;
        if (layerInfo != null) {
            ResourceInfo resourceInfo = layerInfo.getResource();
            String layerName = resourceInfo.getNativeName();

            String requestedCRS = proj;
            String outputCRS = null;
            for (CRSMapper mapper : crsMappers) {
                if (mapper.isSupporting(requestedCRS)) {
                    outputCRS = mapper.getOutputCRS();
                    requestedCRS = outputCRS;
                    break;
                }
            }

            if (resourceInfo != null) {
                String capabilitiesURL = null;
                String tileMatrixSet = null;
                StoreInfo storeInfo = resourceInfo.getStore();
                reason =
                        "RequestedCRS " + requestedCRS + " is not supported by layer: " + layerName;
                if (storeInfo instanceof WMSStoreInfo) {
                    WMSStoreInfo wmsStoreInfo = (WMSStoreInfo) storeInfo;
                    capabilitiesURL = wmsStoreInfo.getCapabilitiesURL();
                    try {
                        WMSCapabilities capabilities =
                                wmsStoreInfo.getWebMapServer(null).getCapabilities();
                        version = capabilities.getVersion();

                        if (!WMS_1_3_0.equals(version)) {
                            version = "1.1.1";
                        }
                        List<Layer> layerList = capabilities.getLayerList();
                        boolean isSupportedCrs = false;
                        for (Layer layer : layerList) {
                            if (layerName.equals(layer.getName())) {
                                isSupportedCrs = isSRSInLayerOrParents(layer, requestedCRS);
                                break;
                            }
                        }
                        isSupportedCrs &= (outputCRS != null);
                        doCascade = isSupportedCrs;
                    } catch (IOException e) {
                        reason =
                                "Unable to extract the WMS remote capabilities. Cascading won't be performed";
                        LOGGER.warning(reason + "due to:" + e);
                        doCascade = false;
                    }
                } else if (storeInfo instanceof WMTSStoreInfo) {
                    WMTSStoreInfo wmtsStoreInfo = (WMTSStoreInfo) storeInfo;
                    capabilitiesURL = wmtsStoreInfo.getCapabilitiesURL();
                    try {
                        WMTSCapabilities capabilities =
                                wmtsStoreInfo.getWebMapTileServer(null).getCapabilities();
                        version = capabilities.getVersion();
                        List<WMTSLayer> layerList = capabilities.getLayerList();
                        boolean isSupportedCrs = false;
                        // Let's check if the capabilities document has a matching layer
                        // supporting a compatible CRS/GridSet
                        for (WMTSLayer layer : layerList) {
                            if (layerName.equals(layer.getName())) {
                                tileMatrixSet =
                                        getSupportedWMTSGridSet(layer, requestedCRS, capabilities);
                                isSupportedCrs = tileMatrixSet != null;
                                break;
                            }
                        }
                        isSupportedCrs &= (outputCRS != null);
                        doCascade = isSupportedCrs;
                    } catch (IOException | FactoryException e) {
                        reason =
                                "Unable to extract the WMTS remote capabilities. Cascading won't be performed";
                        LOGGER.warning(reason + "due to:" + e);
                        doCascade = false;
                    }
                }
                if (doCascade) {
                    // Update the params
                    String[] baseUrlAndPath = getBaseUrlAndPath(capabilitiesURL);
                    baseUrl = baseUrlAndPath[0];
                    path = baseUrlAndPath[1];
                    urlType = URLMangler.URLType.EXTERNAL;
                    refineRequestParams(params, layerName, version, requestedCRS, tileMatrixSet);
                } else {
                    LOGGER.fine("Cascading won't be performed, due to: " + reason);
                }
            }
        }

        String urlTemplate =
                URLDecoder.decode(ResponseUtils.buildURL(baseUrl, path, params, urlType), "UTF-8");
        return urlTemplate;
    }

    private void cleanupCRS(HashMap<String, String> params, String version, String requestedCRS) {
        boolean cleanupCrs = params.containsKey(CRS_PARAM) || params.containsKey(SRS_PARAM);
        if (cleanupCrs) {
            params.remove(CRS_PARAM);
            params.remove(SRS_PARAM);
            String crsName = WMS_1_3_0.equals(version) ? CRS_PARAM : SRS_PARAM;
            params.put(crsName, requestedCRS);
        }
    }

    private void refineRequestParams(
            HashMap<String, String> params,
            String layerName,
            String version,
            String requestedCRS,
            String tileMatrixSetName) {
        String requestType = params.get(REQUEST);
        String service = params.get(SERVICE);
        if (params.containsKey(LAYER)) {
            params.put(LAYER, layerName);
        } else if (params.containsKey(LAYERS)) {
            params.put(LAYERS, layerName);
        }
        if (GETMAP.equalsIgnoreCase(requestType) || GETFEATUREINFO.equalsIgnoreCase(requestType)) {
            params.put("version", version);
            if (params.containsKey("query_layers")) {
                params.put("query_layers", layerName);
            }
            if (params.containsKey("info_format")) {
                params.put("info_format", "text/html");
            } else if (params.containsKey("infoformat")) {
                params.put("infoformat", "text/html");
            }
            cleanupCRS(params, version, requestedCRS);
        }
        // Extra settings for WMTS
        if (WMTS.equalsIgnoreCase(service)) {
            String[] tileMatrixSetSchema = tileMatrixSetName.split(";");
            tileMatrixSetName = tileMatrixSetSchema[0];
            if (tileMatrixSetSchema.length == 2) {
                params.put("tilematrix", tileMatrixSetSchema[1] + "{z}");
            }
            if (params.containsKey("tilematrixset")) {
                params.put("tilematrixset", tileMatrixSetName);
            }
            params.remove("style");
        }
    }

    @SuppressWarnings("PMD.UseCollectionIsEmpty")
    private boolean hasVendorParams() {
        GetMapRequest req = mapContent.getRequest();
        Map<String, String> kvpMap = req.getRawKvp();

        // The following vendor params have been retrieved from the WMSRequests class.
        // format options
        Map<String, Object> formatOptions = req.getFormatOptions();
        if (formatOptions != null
                && formatOptions.size() >= 1
                && !formatOptions.containsKey(
                        MapMLConstants.MAPML_WMS_MIME_TYPE_OPTION.toUpperCase())) {
            return true;
        }

        // view params
        if (req.getViewParams() != null && !req.getViewParams().isEmpty()) {
            return true;
        }
        if (req.getEnv() != null && !req.getEnv().isEmpty()) {
            return true;
        }

        if (req.getMaxFeatures() != null
                || req.getRemoteOwsType() != null
                || req.getRemoteOwsURL() != null
                || req.getScaleMethod() != null
                || req.getStartIndex() != null) {
            return true;
        }

        if (!req.getStyleFormat().equals(SLDHandler.FORMAT)) {
            return true;
        }
        if (req.getStyleVersion() != null) {
            return true;
        }

        // Boolean params
        if (req.isTiled() || Boolean.TRUE.equals(req.getValidateSchema())) {
            return true;
        }

        if (hasProperty(
                kvpMap,
                "propertyName",
                "bgcolor",
                "tilesOrigin",
                "palette",
                "interpolations",
                "clip")) {
            return true;
        }

        // numeric params
        if (req.getBuffer() > 0 || Double.compare(req.getAngle(), 0.0) != 0) {
            return true;
        }
        if (req.getCQLFilter() != null && !req.getCQLFilter().isEmpty()) {
            return true;
        }

        return false;
    }

    private boolean hasProperty(Map<String, String> kvpMap, String... properties) {
        for (String property : properties) {
            String prop = kvpMap.get(property);
            if (StringUtils.hasText(prop)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasRestrictingAccessLimits(LayerInfo layerInfo) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        DataAccessLimits accessLimits = resourceAccessManager.getAccessLimits(auth, layerInfo);

        // If there is any access limits effectively affecting the layer
        // we are not going to cascade so that the vendor param can be
        // honored by the local GeoServer
        if (accessLimits != null) {
            Filter readFilter = accessLimits.getReadFilter();
            if (readFilter != null && readFilter != Filter.INCLUDE) {
                return true;
            }
            Geometry geom = null;
            if (accessLimits instanceof WMSAccessLimits) {
                WMSAccessLimits limits = (WMSAccessLimits) accessLimits;
                geom = limits.getRasterFilter();
            }
            if (accessLimits instanceof WMTSAccessLimits) {
                WMTSAccessLimits limits = (WMTSAccessLimits) accessLimits;
                geom = limits.getRasterFilter();
            }
            if (accessLimits instanceof VectorAccessLimits) {
                VectorAccessLimits limits = (VectorAccessLimits) accessLimits;
                geom = limits.getClipVectorFilter();
            }
            if (accessLimits instanceof CoverageAccessLimits) {
                CoverageAccessLimits limits = (CoverageAccessLimits) accessLimits;
                geom = limits.getRasterFilter();
            }
            if (geom != null) {
                return true;
            }
        }
        return false;
    }

    private boolean isSRSSupported(Layer layer, String srs) {
        Set<String> supportedSRS = layer.getSrs();
        return supportedSRS != null && supportedSRS.contains(srs);
    }

    private String getSupportedWMTSGridSet(
            WMTSLayer layer, String srs, WMTSCapabilities capabilities) throws FactoryException {
        TiledCRSParams inputCrs = TiledCRSConstants.lookupTCRS(srs);
        if (inputCrs == null) {
            return null;
        }
        Map<String, TileMatrixSetLink> tileMatrixLinks = layer.getTileMatrixLinks();
        Collection<TileMatrixSetLink> values = tileMatrixLinks.values();
        for (TileMatrixSetLink value : values) {
            String tileMatrixSetName = value.getIdentifier();
            TileMatrixSet tileMatrixSet = capabilities.getMatrixSet(tileMatrixSetName);

            // First check: same CRS
            // Simpler name equality may not work (i.e. urn:ogc:def:crs:EPSG::3857 vs
            // urn:x-ogc:def:crs:EPSG:3857)
            if (!CRS.isEquivalent(
                    CRS.decode(inputCrs.getCode()), CRS.decode(tileMatrixSet.getCrs()))) {
                continue;
            }

            List<TileMatrix> tileMatrices = tileMatrixSet.getMatrices();
            double[] tiledCRSScales = inputCrs.getScales();

            // check same number of levels
            if (tileMatrices.size() != tiledCRSScales.length) {
                continue;
            }
            TileMatrix level0 = tileMatrices.get(0);
            int tiledCRStileSize = inputCrs.getTILE_SIZE();
            if (tiledCRStileSize != level0.getTileHeight()
                    || tiledCRStileSize != level0.getTileWidth()) {
                continue;
            }

            // check same origin
            org.locationtech.jts.geom.Point origin = level0.getTopLeft();
            Point tCRSorigin = inputCrs.getOrigin();

            double deltaCoordinate =
                    tileMatrices.get(tileMatrices.size() - 1).getResolution() * ORIGIN_DELTA;
            if (Math.abs(tCRSorigin.x - origin.getX()) > deltaCoordinate
                    || Math.abs(tCRSorigin.y - origin.getY()) > deltaCoordinate) {
                continue;
            }

            // check same scales
            for (int i = 0; i < tileMatrices.size(); i++) {
                if (Math.abs(tileMatrices.get(i).getDenominator() - tiledCRSScales[i])
                        > SCALE_DELTA) {
                    continue;
                }
            }
            String prefix = findCommonPrefix(tileMatrices);
            if (prefix != null) {
                tileMatrixSetName += (";" + prefix);
            }
            return tileMatrixSetName;
        }
        return null;
    }

    private boolean isSRSInLayerOrParents(Layer layer, String srs) {
        // Check if the current layer supports the SRS
        if (isSRSSupported(layer, srs)) {
            return true;
        }

        // If not, check the parent layers recursively
        Layer parentLayer = layer.getParent();
        while (parentLayer != null) {
            if (isSRSSupported(parentLayer, srs)) {
                return true;
            }
            parentLayer = parentLayer.getParent();
        }

        // Return false if no layer supports the SRS
        return false;
    }

    private String findCommonPrefix(List<TileMatrix> tileMatrixLevels) {
        // Check for levels having a common prefix, e.g.:
        // EPSG:4326:0
        // EPSG:4326:1
        // EPSG:4326:2
        // EPSG:4326:3

        // Since TileMatrix is a {z} level in MapML client, we will
        // prefix the value with the common prefix if available
        if (tileMatrixLevels == null || tileMatrixLevels.isEmpty()) {
            return null;
        }

        // Start with the first level as the prefix candidate
        String prefix = tileMatrixLevels.get(0).getIdentifier();

        // Iterate over the rest of the levels and trim the prefix
        for (int i = 1; i < tileMatrixLevels.size(); i++) {
            while (tileMatrixLevels.get(i).getIdentifier().indexOf(prefix) != 0) {
                // Trim the last character from the prefix until it matches
                prefix = prefix.substring(0, prefix.length() - 1);
                if (prefix.isEmpty()) {
                    return null; // No common prefix found
                }
            }
        }

        // Check if the remaining prefix is actually a valid common prefix (not just a number)
        if (prefix.matches("\\d+")) {
            return null; // A prefix consisting of only numbers is not valid
        }

        return prefix;
    }

    private String[] getBaseUrlAndPath(String capabilitiesURL) {
        try {
            URL url = new URL(capabilitiesURL);
            String protocol = url.getProtocol();
            String host = url.getHost();
            int port = url.getPort();
            String path = "";
            String baseURL = protocol + "://" + host;
            if (port != -1) {
                baseURL += ":" + port;
            }
            // Optionally, add the context path if needed
            String urlPath = url.getPath();
            int contextPathEnd = urlPath.lastIndexOf("/");
            if (contextPathEnd != -1) {
                baseURL += urlPath.substring(0, contextPathEnd);
                path = urlPath.substring(contextPathEnd + 1, urlPath.length());
            }

            return new String[] {baseURL, path};
        } catch (MalformedURLException e) {
            return null;
        }
    }
}

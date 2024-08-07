package org.geoserver.mapml;

import org.geoserver.catalog.LayerInfo;
import org.geoserver.catalog.ResourceInfo;
import org.geoserver.catalog.SLDHandler;
import org.geoserver.catalog.StoreInfo;
import org.geoserver.catalog.WMSStoreInfo;
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
import org.geotools.ows.wms.Layer;
import org.geotools.ows.wms.WMSCapabilities;
import org.geotools.util.logging.Logging;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

public class MapMLRequestMangler {

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
        crsMappers.add(new CRSMapper(Set.of("EPSG:4326", "urn:ogc:def:crs:EPSG::4326", "urn:ogc:def:crs:MapML::WGS84"), "EPSG:4326"));
        crsMappers.add(new CRSMapper(Set.of("EPSG:3857", "urn:ogc:def:crs:EPSG::3857"), "EPSG:3857"));
        crsMappers.add(new CRSMapper(Set.of("EPSG:5936", "urn:ogc:def:crs:EPSG::5936"), "EPSG:5936"));
        crsMappers.add(new CRSMapper(Set.of("EPSG:3978", "urn:ogc:def:crs:EPSG::3978"), "EPSG:3978"));

    }


    private final static String WMS_1_3_0 = "1.3.0";

    private static final Logger LOGGER = Logging.getLogger(MapMLRequestMangler.class);
    private final MapMLDocumentBuilder.MapMLLayerMetadata mapMLLayerMetadata;
    private final String path;
    private final String baseUrlPattern;
    private final String proj;
    private final HashMap<String, String> params;
    private final WMSMapContent mapContent;

    public MapMLRequestMangler(WMSMapContent mapContent, MapMLDocumentBuilder.MapMLLayerMetadata mapMLLayerMetadata,
                               String baseUrlPattern, String path, HashMap<String, String> params, String proj) {
        this.mapContent = mapContent;
        this.mapMLLayerMetadata = mapMLLayerMetadata;
        this.path = path;
        this.params = params;
        this.baseUrlPattern = baseUrlPattern;
        this.proj = proj;

    }

    public String getUrlTemplate() {
        LayerInfo layerInfo = mapMLLayerMetadata.getLayerInfo();
        String urlTemplate = "";
        try {
            if (!canCascade(mapMLLayerMetadata, layerInfo)) {
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
        return  urlTemplate;
    }

    /**
     * Try cascading to the remote Server unless any condition is preventing that
     * (i.e. CRS not supported on the remote server)
     */
    private String tryCascading(String path, HashMap<String, String> params, LayerInfo layerInfo) throws UnsupportedEncodingException {
        String baseUrl = baseUrlPattern;
        String version = "1.3.0";
        boolean doCascade = false;
        if (layerInfo != null) {
            ResourceInfo resourceInfo = layerInfo.getResource();
            String layerName = resourceInfo.getNativeName();

            String requestedCRS = proj;
            String outputCRS = null;
            for (CRSMapper mapper: crsMappers) {
                if (mapper.isSupporting(requestedCRS)) {
                    outputCRS = mapper.getOutputCRS();
                    requestedCRS = outputCRS;
                }
            }

            if (resourceInfo != null) {
                StoreInfo storeInfo = resourceInfo.getStore();
                if (storeInfo instanceof WMSStoreInfo) {
                    WMSStoreInfo wmsStoreInfo = (WMSStoreInfo) storeInfo;
                    try {
                        WMSCapabilities capabilities = wmsStoreInfo.getWebMapServer(null).getCapabilities();
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
                        isSupportedCrs |= (outputCRS != null);
                        doCascade = isSupportedCrs;
                    } catch (IOException e) {
                        LOGGER.warning("Unable to extract the WMS remote capabilities. Cascading won't be performed");
                        doCascade = false;
                    }
                    if (doCascade) {
                        // Update the params
                        baseUrl =  getBaseUrl(wmsStoreInfo.getCapabilitiesURL());
                        params.put("version", version);
                        boolean cleanupCrs = params.containsKey("crs") || params.containsKey("srs");
                        if (params.containsKey("layer")) {
                            params.put("layer", layerName);
                        } else if (params.containsKey("layers")) {
                            params.put("layers", layerName);
                        }
                        if (params.containsKey("tilematrixset")){
                            params.put("tilematrixset", requestedCRS);
                        }

                        if (cleanupCrs) {
                            params.remove("crs");
                            params.remove("srs");
                            String crsName = WMS_1_3_0.equals(version) ? "crs" : "srs";
                            params.put(crsName, requestedCRS);
                        }

                    }

                }
            }
        }


        URLMangler.URLType urlType = doCascade ? URLMangler.URLType.EXTERNAL : URLMangler.URLType.SERVICE;
        String urlTemplate = URLDecoder.decode(
                ResponseUtils.buildURL(baseUrl, path, params, urlType),
                "UTF-8");
        return urlTemplate;
    }


    private boolean canCascade(MapMLDocumentBuilder.MapMLLayerMetadata metadata, LayerInfo layerInfo) {
        if (metadata.isUseRemote()) {
            if (hasRestrictingAccessLimits(layerInfo)) return false;
            return !hasVendorParams();
        }
        return false;
    }

    private boolean hasVendorParams() {
        GetMapRequest req = mapContent.getRequest();
        Map<String, String> kvpMap = req.getRawKvp();

        // The following vendor params have been retrieved from the WMSRequests class.
        // format options
        Map<String, Object> formatOptions = req.getFormatOptions();
        if (formatOptions != null && formatOptions.size() >= 1 &&
                ! formatOptions.containsKey(MapMLConstants.MAPML_WMS_MIME_TYPE_OPTION.toUpperCase())) {
            return true;

        }

        // view params
        if (req.getViewParams() != null && !req.getViewParams().isEmpty()) {
            return true;
        }
        if (req.getEnv() != null && !req.getEnv().isEmpty()) {
            return true;
        }

        if (req.getMaxFeatures() != null ||
                req.getRemoteOwsType() != null
                ||req.getRemoteOwsURL() != null
                ||req.getScaleMethod() != null
                ||req.getStartIndex() != null){
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

        if (hasProperty(kvpMap, "propertyName", "bgcolor", "tilesOrigin", "palette")) {
            return true;
        }

        // numeric params
        if (req.getBuffer() > 0 || Double.compare(req.getAngle(), 0.0) != 0) {
            return true;
        }

        return false;
    }

    private boolean hasProperty(Map<String, String> kvpMap, String... properties) {
        for (String property: properties) {
            String prop = kvpMap.get(property);
            if (StringUtils.hasText(prop)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasRestrictingAccessLimits(LayerInfo layerInfo) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        ResourceAccessManager resourceAccessManager = GeoServerExtensions.bean(ResourceAccessManager.class);
        DataAccessLimits accessLimits = resourceAccessManager.getAccessLimits(auth, layerInfo);
        if (accessLimits != null) {
            Filter readFilter = accessLimits.getReadFilter();
            if (readFilter != null && readFilter != Filter.INCLUDE) {
                return true;
            }
            if (accessLimits instanceof WMSAccessLimits){
                WMSAccessLimits limits = (WMSAccessLimits) accessLimits;
                if (limits.getRasterFilter() != null) {
                    return true;
                }
            }
            if (accessLimits instanceof WMTSAccessLimits){
                WMTSAccessLimits limits = (WMTSAccessLimits) accessLimits;
                if (limits.getRasterFilter() != null) {
                    return true;
                }
            }
            if (accessLimits instanceof VectorAccessLimits){
                VectorAccessLimits limits = (VectorAccessLimits) accessLimits;
                if (limits.getClipVectorFilter() != null) {
                    return true;
                }
            }
            if (accessLimits instanceof CoverageAccessLimits){
                CoverageAccessLimits limits = (CoverageAccessLimits) accessLimits;
                if (limits.getRasterFilter() != null) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean isSRSSupported(Layer layer, String srs) {
        Set<String> supportedSRS = layer.getSrs();
        return supportedSRS != null && supportedSRS.contains(srs);
    }

    public boolean isSRSInLayerOrParents(Layer layer, String srs) {
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

    private String getBaseUrl(String capabilitiesURL) {
        try {
            URL url = new URL(capabilitiesURL);
            String protocol = url.getProtocol();
            String host = url.getHost();
            int port = url.getPort();
            String baseURL = protocol + "://" + host;
            if (port != -1) {
                baseURL += ":" + port;
            }
            // Optionally, add the context path if needed
            String path = url.getPath();
            int contextPathEnd = path.indexOf("/", 1);
            if (contextPathEnd != -1) {
                baseURL += path.substring(0, contextPathEnd + 1);
            }
            return baseURL;
        } catch (MalformedURLException e) {
            return null;
        }
    }
}

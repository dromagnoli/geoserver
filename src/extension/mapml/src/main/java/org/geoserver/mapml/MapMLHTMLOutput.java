/* (c) 2024 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.mapml;

import static org.apache.commons.text.StringEscapeUtils.escapeHtml4;

import javax.servlet.http.HttpServletRequest;
import org.geoserver.mapml.tcrs.Bounds;
import org.geoserver.mapml.tcrs.Point;
import org.geoserver.mapml.tcrs.TiledCRS;
import org.geoserver.mapml.tcrs.WrappingProjType;
import org.geoserver.ows.URLMangler;
import org.geoserver.ows.util.ResponseUtils;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.referencing.CRS;

/** Class delegated to build an HTML Document embedding a MapML Viewer. */
public class MapMLHTMLOutput {

    private String layerLabel;
    private HttpServletRequest request;
    private WrappingProjType projType;
    private String sourceUrL;
    private int zoom = 0;
    private Double latitude = 0.0;
    private Double longitude = 0.0;
    private ReferencedEnvelope projectedBbox;
    private String templateHeader;

    private MapMLHTMLOutput(HTMLOutputBuilder builder) {
        this.latitude = builder.latitude;
        this.longitude = builder.longitude;
        this.zoom = builder.zoom;
        this.layerLabel = builder.layerLabel;
        this.request = builder.request;
        this.projType = builder.projType;
        this.sourceUrL = builder.sourceUrL;
        this.projectedBbox = builder.projectedBbox;
        this.templateHeader = builder.templateHeader;
    }

    public static class HTMLOutputBuilder {
        private String layerLabel;
        private HttpServletRequest request;
        private WrappingProjType projType;
        private String sourceUrL;
        private ReferencedEnvelope projectedBbox;
        private int zoom = 0;
        private Double latitude = 0.0;
        private Double longitude = 0.0;
        private String templateHeader = "";

        public HTMLOutputBuilder setLayerLabel(String layerLabel) {
            this.layerLabel = layerLabel;
            return this;
        }

        public HTMLOutputBuilder setRequest(HttpServletRequest request) {
            this.request = request;
            return this;
        }

        public HTMLOutputBuilder setProjType(WrappingProjType projType) {
            this.projType = projType;
            return this;
        }

        public HTMLOutputBuilder setSourceUrL(String sourceUrL) {
            this.sourceUrL = sourceUrL;
            return this;
        }

        public HTMLOutputBuilder setTemplateHeader(String templateHeader) {
            this.templateHeader = templateHeader;
            return this;
        }

        public HTMLOutputBuilder setProjectedBbox(ReferencedEnvelope projectedBbox) {
            this.projectedBbox = projectedBbox;
            return this;
        }

        public HTMLOutputBuilder setLatitude(Double latitude) {
            this.latitude = latitude;
            return this;
        }

        public HTMLOutputBuilder setLongitude(Double longitude) {
            this.longitude = longitude;
            return this;
        }

        public MapMLHTMLOutput build() {
            return new MapMLHTMLOutput(this);
        }
    }

    public String toHTML() {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>\n")
                .append("<html>\n")
                .append("<head>\n")
                .append("<title>")
                .append(escapeHtml4(layerLabel))
                .append("</title>\n")
                .append("<meta charset='utf-8'>\n")
                .append("<script type=\"module\"  src=\"")
                .append(buildViewerPath(request))
                .append("\"></script>\n")
                .append("<style>\n")
                .append("html, body { height: 100%; }\n")
                .append("* { margin: 0; padding: 0; }\n")
                .append(
                        "mapml-viewer:defined { max-width: 100%; width: 100%; height: 100%; border: none; vertical-align: middle }\n")
                .append("mapml-viewer:not(:defined) > * { display: none; } n")
                .append("layer- { display: none; }\n")
                .append("</style>\n")
                .append(buildProjectionScript(projType))
                .append("<noscript>\n")
                .append("<style>\n")
                .append("mapml-viewer:not(:defined) > :not(layer-) { display: initial; }\n")
                .append("</style>\n")
                .append("</noscript>\n")
                .append(templateHeader)
                .append("</head>\n")
                .append("<body>\n")
                .append("<mapml-viewer projection=\"")
                .append(projType.getTiledCRS().getParams().getName())
                .append("\" ")
                .append("zoom=\"")
                .append(computeZoom(projType, projectedBbox))
                .append("\" lat=\"")
                .append(latitude)
                .append("\" ")
                .append("lon=\"")
                .append(longitude)
                .append("\" controls controlslist=\"geolocation\">\n")
                .append("<layer- label=\"")
                .append(escapeHtml4(layerLabel))
                .append("\" ")
                .append("src=\"")
                .append(sourceUrL)
                .append("\" checked></layer->\n")
                .append("</mapml-viewer>\n")
                .append("</body>\n")
                .append("</html>");
        return sb.toString();
    }

    private String buildProjectionScript(WrappingProjType projType) {
        String projectionScript = "";
        if (!projType.isBuiltIn()) {
            StringBuilder sb = new StringBuilder();
            sb.append("<script type=\"module\">\n")
                    .append("let customProjectionDefinition = `\n")
                    .append("{\n")
                    .append(projType.getTiledCRS().getParams().buildDefinition(10))
                    .append(",\n")
                    .append("          \"proj4string\" : \"")
                    .append(getProjString())
                    .append("\"\n")
                    .append("}`;\n")
                    .append("let map = document.querySelector(\"mapml-viewer\");\n")
                    .append("let cProjection = map.defineCustomProjection(customProjectionDefinition);\n")
                    .append("map.projection = cProjection;\n")
                    .append("</script>");
            projectionScript = sb.toString();
        }
        return projectionScript;
    }

    private String getProjString() {
        return "+proj=tmerc +datum=WGS84 +lat_0=0 +lon_0=-99 +k=0.9996 +x_0=500000 +y_0=0 +units=m +no_defs +type=crs";
    }

    private String buildViewerPath(HttpServletRequest request) {
        String base = ResponseUtils.baseURL(request);
        return ResponseUtils.buildURL(
                base, "/mapml/viewer/widget/mapml-viewer.js", null, URLMangler.URLType.RESOURCE);
    }

    private int computeZoom(WrappingProjType projType, ReferencedEnvelope projectedBbox) {
        //TODO FIXME
        TiledCRS tcrs = projType.getTiledCRS();
        boolean flipAxis =
                CRS.getAxisOrder(projectedBbox.getCoordinateReferenceSystem())
                        .equals(CRS.AxisOrder.NORTH_EAST);
        double minX = flipAxis ? projectedBbox.getMinY() : projectedBbox.getMinX();
        double maxX = flipAxis ? projectedBbox.getMaxY() : projectedBbox.getMaxX();
        double minY = flipAxis ? projectedBbox.getMinX() : projectedBbox.getMinY();
        double maxY = flipAxis ? projectedBbox.getMaxX() : projectedBbox.getMaxY();
        final Bounds pb = new Bounds(new Point(minX, minY), new Point(maxX, maxY));
        // allowing for the data to be displayed at a certain size (WxH) in pixels,
        // figure out the zoom level at which the projected bounds fits into that,
        // in both dimensions
        zoom =
                tcrs.fitProjectedBoundsToDisplay(
                        pb, MapMLConstants.DISPLAY_BOUNDS_DESKTOP_LANDSCAPE);
        return zoom;
    }
}

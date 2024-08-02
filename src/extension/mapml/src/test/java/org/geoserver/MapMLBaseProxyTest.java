package org.geoserver;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.Assert.assertTrue;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import java.io.ByteArrayInputStream;
import java.io.StringWriter;
import java.util.Map;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.custommonkey.xmlunit.XMLUnit;
import org.custommonkey.xmlunit.XpathEngine;
import org.geoserver.mapml.MapMLConstants;
import org.geoserver.mapml.MapMLTestSupport;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.w3c.dom.Document;

public class MapMLBaseProxyTest extends MapMLTestSupport {

    protected static String getBaseCapabilitiesURL() {
        return "/mockgeoserver/wms?service=WMS&VERSION=1.3.0&request=GetCapabilities";
    }

    protected static final String BASE_REQUEST =
            "wms?LAYERS=cascadedLayer"
                    + "&STYLES=&FORMAT="
                    + MapMLConstants.MAPML_MIME_TYPE
                    + "&SERVICE=WMS&VERSION=1.3.0"
                    + "&REQUEST=GetMap"
                    + "&SRS=EPSG:4326"
                    + "&BBOX=0,0,1,1"
                    + "&WIDTH=150"
                    + "&HEIGHT=150"
                    + "&format_options="
                    + MapMLConstants.MAPML_WMS_MIME_TYPE_OPTION
                    + ":image/png";

    protected XpathEngine xpath;

    protected static WireMockServer mockService;

    @Override
    protected void registerNamespaces(Map<String, String> namespaces) {
        namespaces.put("wms", "http://www.opengis.net/wms");
        namespaces.put("ows", "http://www.opengis.net/ows");
        namespaces.put("html", "http://www.w3.org/1999/xhtml");
    }

    @BeforeClass
    public static void beforeClass() {
        WireMockConfiguration config = wireMockConfig().dynamicPort();
        mockService = new WireMockServer(config);
        mockService.start();
        mockService.stubFor(
                WireMock.get(
                                urlEqualTo(getBaseCapabilitiesURL()))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader("Content-Type", MediaType.TEXT_XML_VALUE)
                                        .withBodyFile("wmscaps.xml")));
    }

    @AfterClass
    public static void afterClass() throws Exception {
        mockService.shutdown();
    }

    @Before
    public void setup() {
        xpath = XMLUnit.newXpathEngine();
    }

    protected void checkCascading(String path, boolean shouldCascade, int port) throws Exception {
        Document doc = getMapML(path);
        String url = xpath.evaluate("//html:map-link[@rel='image']/@tref", doc);
        assertCascading(shouldCascade, url, port);

        url = xpath.evaluate("//html:map-link[@rel='query']/@tref", doc);
        assertCascading(shouldCascade, url, port);
    }

    protected void assertCascading(boolean shouldCascade, String url, int port) {
        if (shouldCascade) {
            assertTrue(url.startsWith("http://localhost:" + port + "/mockgeoserver"));
            assertTrue(url.contains("layers=states"));
        } else {
            assertTrue(url.startsWith("http://localhost:8080/geoserver"));
            assertTrue(url.contains("layers=cascadedLayer"));
        }
    }

    /**
     * Executes a request using the GET method and returns the result as an MapML document.
     *
     * @param path The portion of the request after the context, example:
     * @return A result of the request parsed into a dom.
     */
    protected org.w3c.dom.Document getMapML(final String path) throws Exception {
        MockHttpServletRequest request = createRequest(path, false);
        request.addHeader("Accept", "text/mapml");
        request.setMethod("GET");
        request.setContent(new byte[] {});
        String resp = dispatch(request, "UTF-8").getContentAsString();
        return dom(new ByteArrayInputStream(resp.getBytes()), true);
    }

    /** For debugging purposes */
    public static void printDocument(Document doc) throws TransformerException {
        // Initialize a transformer
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();

        // Set output properties for the transformer (optional, for pretty print)
        transformer.setOutputProperty(javax.xml.transform.OutputKeys.INDENT, "yes");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");

        // Convert the DOM Document to a String
        StringWriter writer = new StringWriter();
        StreamResult result = new StreamResult(writer);
        DOMSource source = new DOMSource(doc);
        transformer.transform(source, result);

        // Return the XML string
        // System.out.println(writer.toString());
    }
}

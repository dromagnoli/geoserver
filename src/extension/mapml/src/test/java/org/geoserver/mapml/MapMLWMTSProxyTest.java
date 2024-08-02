/* (c) 2023 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.mapml;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.geoserver.MapMLBaseProxyTest;
import org.geoserver.catalog.Catalog;
import org.geoserver.catalog.LayerInfo;
import org.geoserver.catalog.ResourceInfo;
import org.geoserver.catalog.WMTSLayerInfo;
import org.geoserver.catalog.WMTSStoreInfo;
import org.geoserver.data.test.SystemTestData;
import org.geoserver.gwc.GWC;
import org.geoserver.gwc.config.GWCConfig;
import org.geoserver.gwc.layer.GeoServerTileLayer;
import org.geoserver.mapml.gwc.gridset.MapMLGridsets;
import org.geowebcache.grid.GridSubset;
import org.geowebcache.mime.TextMime;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.springframework.http.MediaType;
import org.w3c.dom.Document;

import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.geowebcache.grid.GridSubsetFactory.createGridSubSet;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class MapMLWMTSProxyTest extends MapMLBaseProxyTest {

    @Override
    protected void registerNamespaces(Map<String, String> namespaces) {
        super.registerNamespaces(namespaces);
        namespaces.put("wmts", "http://www.opengis.net/wmts/1.0");
        namespaces.put("gml", "http://www.opengis.net/gml");
        namespaces.put("xlink=", "http://www.w3.org/1999/xlink");
    }

    protected static String getWMTSCapabilitiesURL() {
        return "/mockgeoserver/gwc/service/wmts?service=WMTS&version=1.0.0&request=GetCapabilities";
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
                                        .withBodyFile("wmtscaps.xml")));
    }

    @Override
    protected void onSetUp(SystemTestData testData) throws Exception {
        super.onSetUp(testData);
        Catalog catalog = getCatalog();

        WMTSStoreInfo wmtsStore = catalog.getFactory().createWebMapTileServer();
        wmtsStore.setName("wmtsStore");
        wmtsStore.setWorkspace(catalog.getDefaultWorkspace());
        wmtsStore.setCapabilitiesURL(
                "http://localhost:"
                        + mockService.port()
                        + getWMTSCapabilitiesURL());
        wmtsStore.setEnabled(true);
        catalog.add(wmtsStore);

        // Create WMSLayerInfo using the Catalog factory
        WMTSLayerInfo wmtsLayer = catalog.getFactory().createWMTSLayer();
        wmtsLayer.setName("cascadedLayer");
        wmtsLayer.setNativeName("states");
        wmtsLayer.setStore(wmtsStore);
        wmtsLayer.setAdvertised(true);
        wmtsLayer.setEnabled(true);

        // Add the layer to the catalog
        LayerInfo layerInfo = catalog.getFactory().createLayer();
        layerInfo.setResource(wmtsLayer);
        layerInfo.setDefaultStyle(catalog.getStyleByName("default"));
        catalog.add(wmtsLayer);
        catalog.add(layerInfo);

        GWC gwc = applicationContext.getBean(GWC.class);
        GWCConfig defaults = GWCConfig.getOldDefaults();
        // it seems just the fact of retrieving the bean causes the
        // GridSets to be added to the gwc GridSetBroker, but if you don't do
        // this, they are not added automatically
        MapMLGridsets mgs = applicationContext.getBean(MapMLGridsets.class);
        GridSubset wgs84gridset = createGridSubSet(mgs.getGridSet("WGS84").get());
        GridSubset osmtilegridset = createGridSubSet(mgs.getGridSet("OSMTILE").get());

        GeoServerTileLayer layerInfoTileLayer =
                new GeoServerTileLayer(layerInfo, defaults, gwc.getGridSetBroker());
        layerInfoTileLayer.addGridSubset(wgs84gridset);
        layerInfoTileLayer.addGridSubset(osmtilegridset);
        layerInfoTileLayer.getInfo().getMimeFormats().add(TextMime.txtMapml.getMimeType());
        gwc.save(layerInfoTileLayer);
    }

    @Test
    @Ignore
    public void testRemoteVsNotRemote() throws Exception {
        Catalog cat = getCatalog();
        // Verify the layer was added
        LayerInfo li = cat.getLayerByName("cascadedLayer");
        assertNotNull(li);
        assertEquals("cascadedLayer", li.getName());

        ResourceInfo layerMeta = li.getResource();
        layerMeta.getMetadata().put("mapml.useRemote", false);
        layerMeta.getMetadata().put("mapml.useTiles", true);
        cat.save(layerMeta);

        // get the mapml doc for the layer
        String path =
                "/wmts?request=GetTile&"
                        + "tilematrixset=WGS84&"
                        + "tilematrix=4&"
                        + "TileRow=4"
                        + "&service=WMTS"
                        + "&TileCol=9"
                        + "&version=1.0.0"
                        + "&layer=cascadedLayer"
                        + "&format_options="
                        + MapMLConstants.MAPML_WMS_MIME_TYPE_OPTION
                        + ":image/png";

        Document doc = getMapML(path);
        //printDocument(doc);
        String url = xpath.evaluate("//html:map-link[@rel='image']/@tref", doc);
        assertTrue(url.startsWith("http://localhost:8080/geoserver"));

        // Now switching to use Remote URL
        layerMeta.getMetadata().put("mapml.useRemote", true);
        cat.save(layerMeta);
        doc = getMapML(path);
        url = xpath.evaluate("//html:map-link[@rel='image']/@tref", doc);

        assertTrue(url.startsWith("http://localhost:" + mockService.port() + "/mockgeoserver/gwc"));
        //printDocument(doc);
    }

}

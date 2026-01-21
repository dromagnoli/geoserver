package org.geoserver.wms.vector;

import static org.junit.Assert.*;

import no.ecc.vectortile.VectorTileDecoder;
import org.geoserver.catalog.LayerInfo;
import org.geoserver.gwc.GWC;
import org.geoserver.gwc.config.GWCConfig;
import org.geoserver.gwc.layer.GeoServerTileLayer;
import org.geoserver.gwc.layer.GeoServerTileLayerInfo;
import org.geoserver.test.GeoServerSystemTestSupport;
import org.geoserver.data.test.SystemTestData;
import org.geowebcache.layer.TileLayer;
import org.junit.Before;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.List;

/**
 * Integration test: MVT vector tiles cached via GWC with metatiling enabled/disabled.
 *
 * <p>What we assert: - With metatiling disabled (meta 1x1), requesting a tile does NOT pre-seed neighbors in cache. -
 * With metatiling enabled (meta 4x4), requesting a tile pre-seeds other tiles in the same metatile.
 */
public class MVTMetatilingTest extends GeoServerSystemTestSupport {

    private static final String LAYER_NAME = "cite:BasicPolygons";
    private static final String GRIDSET_ID = "EPSG:4326";
    private static final String MVT_FORMAT = "application/vnd.mapbox-vector-tile";

    private static final int[][] EXPECTED_FEATURE_COUNTS = {
            {2, 1},
            {2, 1}};
    private static final String[][] EXPECTED_GEOMETRIES = {
            {"POLYGON ((182.0625 258.75, -2.75 258.75, -2.75 -2.75, 182.0625 -2.75, 182.0625 258.75))",
            "POLYGON ((108.0625 258.75, -2.75 258.75, -2.75 113.75, 108.0625 113.75, 108.0625 258.75))"},
            {"POLYGON ((-2.75 221.875, -2.75 -2.75, 182.0625 -2.75, 182.0625 221.875, -2.75 221.875))",
            "POLYGON ((108.0625 258.75, -2.75 258.75, -2.75 -2.75, 108.0625 -2.75, 108.0625 258.75))"}};

    private GWC gwc;

    @Before
    public void init() throws Exception {
        gwc = GWC.get();
        // Make sure the layer exists in the test dataset
        assertNotNull(getCatalog().getLayerByName(LAYER_NAME));
    }

    @Override
    protected void onSetUp(SystemTestData testData) throws Exception {
        super.onSetUp(testData);
        // If you need specific data, you can add it here; cite:roads is usually present in SystemTestData.
    }

    /** Configures the GWC layer for MVT and sets metatiling parameters. */
    private void configureGwcLayer(int metaW, int metaH) throws Exception {
        // Ensure GWC is enabled globally
        GWCConfig cfg = gwc.getConfig();
        cfg.setDirectWMSIntegrationEnabled(true);
        gwc.saveConfig(cfg);

        LayerInfo layerInfo = getCatalog().getLayerByName(LAYER_NAME);
        assertNotNull(layerInfo);

        GeoServerTileLayer tileLayer = (GeoServerTileLayer) gwc.getTileLayerByName(LAYER_NAME);

        // If it doesn't exist yet, create it
        if (tileLayer == null) {
            gwc.add(new GeoServerTileLayer(layerInfo, gwc.getConfig(), gwc.getGridSetBroker()));
            tileLayer = (GeoServerTileLayer) gwc.getTileLayerByName(LAYER_NAME);
        }

        GeoServerTileLayerInfo info = tileLayer.getInfo();

        // Enable caching for this layer
        info.setEnabled(true);

        // Ensure MVT is among cached formats
        // (GeoServer/GWC sometimes stores this as MimeType or Strings depending on version)
        info.getMimeFormats().clear();
        info.getMimeFormats().add(MVT_FORMAT);

        // Metatiling settings
        info.setMetaTilingX(metaW);
        info.setMetaTilingY(metaH);

        // For vector tiles you generally want gutter = 0 unless you have a specific reason
        info.setGutter(0);
        gwc.save(tileLayer);

        // Clear cache between runs for determinism
        truncateCacheForLayer(LAYER_NAME);
    }

    private void truncateCacheForLayer(String layerName) throws Exception {
        TileLayer tl = gwc.getTileLayerByName(layerName);
        assertNotNull(tl);
        gwc.layerRemoved(layerName);
    }

    @Test
    public void testMetatilingDisabled() throws Exception {
        configureGwcLayer(1, 1);
        int z = 7, x = 128, y = 60;
        VectorTileDecoder decoder = new VectorTileDecoder();

        for (int j=0; j<2; j++) {
            for (int i=0; i<2; i++) {
                MockHttpServletResponse resp = requestMvtWmtsTileResp(z, x + i, y + j);
                assertEquals(200, resp.getStatus());
                byte[] data = resp.getContentAsByteArray();
                // All requests should be cache misses
                assertCacheResult(resp, CacheResult.MISS);
                assertTrue(data.length > 0);
                assertFeature(decoder, data, i, j);
            }
        }
    }

    @Test
    public void testMetatilingEnabled() throws Exception {
        configureGwcLayer(2, 2);
        int z = 7, x = 128, y = 60;
        boolean first = true;
        VectorTileDecoder decoder = new VectorTileDecoder();
        for (int j=0; j<2; j++) {
            for (int i=0; i<2; i++) {
                MockHttpServletResponse resp = requestMvtWmtsTileResp( z, x + i, y + j);
                assertEquals(200, resp.getStatus());
                byte[] data = resp.getContentAsByteArray();
                assertTrue(data.length > 0);
                assertFeature(decoder, data, i, j);
                if (first) {
                    assertCacheResult(resp, CacheResult.MISS);
                    first = false;
                } else {
                    assertCacheResult(resp, CacheResult.HIT);
                }
            }
        }
    }

    private MockHttpServletResponse requestMvtWmtsTileResp(int z, int x, int y)
            throws Exception {

        String url =
                "gwc/service/wmts?SERVICE=WMTS&REQUEST=GetTile&VERSION=1.0.0"
                        + "&LAYER=" + LAYER_NAME
                        + "&STYLE="
                        + "&TILEMATRIXSET=" + GRIDSET_ID
                        + "&TILEMATRIX=" + GRIDSET_ID + ":" + z
                        + "&TILEROW=" + y
                        + "&TILECOL=" + x
                        + "&FORMAT=" + MVT_FORMAT;

        MockHttpServletResponse resp = getAsServletResponse(url);
        assertEquals(200, resp.getStatus());
        assertNotNull(resp.getContentType());
        assertTrue("Unexpected content-type: " + resp.getContentType(),
                resp.getContentType().startsWith(MVT_FORMAT));
        return resp;
    }

    enum CacheResult { HIT, MISS }

    private void assertCacheResult(MockHttpServletResponse resp, CacheResult expected) {
        // Header names vary slightly across versions / servlet containers
        String v =
                firstNonNullHeader(resp,
                        "geowebcache-cache-result",
                        "GeoWebCache-Cache-Result",
                        "X-GeoWebCache-Cache-Result",
                        "X-GWC-Cache-Result",
                        "X-GWC-Cache-Result".toLowerCase()
                );

        assertNotNull("No cache result header found; available headers: " + resp.getHeaderNames(), v);

        // values often: "HIT", "MISS", sometimes "hit"/"miss"
        String norm = v.trim().toUpperCase();
        assertEquals("Unexpected cache result header value: " + v, expected.name(), norm);
    }

    private String firstNonNullHeader(MockHttpServletResponse resp, String... names) {
        for (String n : names) {
            String v = resp.getHeader(n);
            if (v != null) return v;
        }
        // Some MockHttpServletResponse impls normalize header names; brute-force scan
        for (String hn : resp.getHeaderNames()) {
            String up = hn.toUpperCase();
            if (up.contains("CACHE") && up.contains("RESULT")) {
                return resp.getHeader(hn);
            }
        }
        return null;
    }

    private void assertFeature(VectorTileDecoder decoder, byte[] data, int column, int row) throws IOException {
        VectorTileDecoder.FeatureIterable decoded = decoder.decode(data);
        List<VectorTileDecoder.Feature> list = decoded.asList();
        assertEquals(EXPECTED_FEATURE_COUNTS[row][column], list.size());
        VectorTileDecoder.Feature feature = list.get(0);
        assertEquals(EXPECTED_GEOMETRIES[row][column], feature.getGeometry().toString());
    }
}

/* (c) 2023 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.mapml;

import java.util.Collections;
import java.util.List;
import org.geoserver.MapMLBaseProxyTest;
import org.geoserver.catalog.Catalog;
import org.geoserver.catalog.LayerInfo;
import org.geoserver.catalog.WMSLayerInfo;
import org.geoserver.catalog.WMSStoreInfo;
import org.geoserver.data.test.SystemTestData;
import org.geoserver.security.CatalogMode;
import org.geoserver.security.DataAccessLimits;
import org.geoserver.security.TestResourceAccessManager;
import org.geotools.api.filter.Filter;
import org.geotools.api.filter.FilterFactory;
import org.geotools.factory.CommonFactoryFinder;
import org.geotools.geometry.jts.JTSFactoryFinder;
import org.junit.Test;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.io.WKTReader;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;

public class MapMLWMSDataAccessProxyTest extends MapMLBaseProxyTest {

    @Override
    protected void setUpSpring(List<String> springContextLocations) {
        super.setUpSpring(springContextLocations);
        springContextLocations.add("classpath:/org/geoserver/wms/ResourceAccessManagerContext.xml");
    }

    @Override
    protected void onSetUp(SystemTestData testData) throws Exception {
        super.onSetUp(testData);
        Catalog catalog = getCatalog();

        WMSStoreInfo wmsStore = catalog.getFactory().createWebMapServer();
        wmsStore.setName("wmsStore");
        wmsStore.setWorkspace(catalog.getDefaultWorkspace());
        wmsStore.setCapabilitiesURL(
                "http://localhost:"
                        + mockService.port()
                        + getBaseCapabilitiesURL());
        wmsStore.setEnabled(true);
        catalog.add(wmsStore);

        // Create A layer with access limits
        WMSLayerInfo wmsLayer2 = catalog.getFactory().createWMSLayer();
        wmsLayer2.setName("cascadedLayerAccessLimits");
        wmsLayer2.setNativeName("states");
        wmsLayer2.setStore(wmsStore);
        wmsLayer2.setAdvertised(true);
        wmsLayer2.setEnabled(true);
        wmsLayer2.getMetadata().put("mapml.useRemote", true);

        LayerInfo layer2 = catalog.getFactory().createLayer();
        layer2.setResource(wmsLayer2);
        layer2.setDefaultStyle(catalog.getStyleByName("default"));
        catalog.add(wmsLayer2);
        catalog.add(layer2);

        TestResourceAccessManager tam =
                (TestResourceAccessManager) applicationContext.getBean("testResourceAccessManager");

        String username = "testUser";
        String password = "testPassword";
        SimpleGrantedAuthority authority = new SimpleGrantedAuthority("ROLE_USER");
        User user = new User(username, password, Collections.singletonList(authority));
        Authentication auth =
                new UsernamePasswordAuthenticationToken(user, password, user.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);

        String wkt =
                "POLYGON((-31.266001 34.307144, 39.869301 34.307144, 39.869301 71.185474, -31.266001 71.185474, -31.266001 34.307144))";
        GeometryFactory geometryFactory = JTSFactoryFinder.getGeometryFactory();
        WKTReader wktReader = new WKTReader(geometryFactory);
        Polygon polygon = (Polygon) wktReader.read(wkt);

        // Create a filter that performs a spatial intersection with the polygon
        FilterFactory FF = CommonFactoryFinder.getFilterFactory();
        Filter intersectsFilter = FF.intersects(FF.property("the_geom"), FF.literal(polygon));
        tam.putLimits(
                username, layer2, new DataAccessLimits(CatalogMode.CHALLENGE, intersectsFilter));
    }

    @Test
    public void testMapMLNotCascadingWithAccessLimits() throws Exception {
        // get the mapml doc for the layer
        String path = BASE_REQUEST.replace("cascadedLayer", "cascadedLayerAccessLimits");

        checkCascading(path, false, mockService.port());
    }
}

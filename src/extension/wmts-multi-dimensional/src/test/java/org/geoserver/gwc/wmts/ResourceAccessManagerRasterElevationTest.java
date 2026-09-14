/* (c) 2026 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.gwc.wmts;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;

import java.util.Collections;
import java.util.List;
import org.geoserver.catalog.CoverageInfo;
import org.geoserver.catalog.DimensionInfo;
import org.geoserver.catalog.DimensionPresentation;
import org.geoserver.catalog.LayerInfo;
import org.geoserver.catalog.ResourceInfo;
import org.geoserver.data.test.SystemTestData;
import org.geoserver.gwc.wmts.dimensions.Dimension;
import org.geoserver.gwc.wmts.dimensions.DimensionsUtils;
import org.geoserver.gwc.wmts.dimensions.RasterElevationDimension;
import org.geoserver.platform.GeoServerExtensions;
import org.geoserver.security.CatalogMode;
import org.geoserver.security.CoverageAccessLimits;
import org.geoserver.security.ResourceAccessManager;
import org.geoserver.security.TestResourceAccessManager;
import org.geotools.api.filter.Filter;
import org.geotools.api.filter.FilterFactory;
import org.geotools.factory.CommonFactoryFinder;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.w3c.dom.Document;

/**
 * Checks the granule level restrictions declared by a {@link ResourceAccessManager} are applied to the raster domains
 * reported by describeDomains and getHistogram.
 */
public class ResourceAccessManagerRasterElevationTest extends TestsSupport {

    /** Add the test resource access manager in the spring context */
    @Override
    protected void setUpSpring(List<String> springContextLocations) {
        super.setUpSpring(springContextLocations);
        springContextLocations.add("classpath:/ResourceAccessManagerContext.xml");
    }

    /** Enable the Spring Security auth filters */
    @Override
    protected List<jakarta.servlet.Filter> getFilters() {
        return Collections.singletonList((jakarta.servlet.Filter) GeoServerExtensions.bean("filterChainProxy"));
    }

    /** describeDomains only reports dimensions declared on the layer, so the elevation one has to be registered. */
    @Override
    protected void afterSetup(SystemTestData testData) {
        CoverageInfo raster = getCatalog().getCoverageByName(RASTER_ELEVATION.getLocalPart());
        registerLayerDimension(raster, ResourceInfo.ELEVATION, null, DimensionPresentation.LIST, minimumValue());
    }

    @Override
    protected void onSetUp(SystemTestData testData) throws Exception {
        super.onSetUp(testData);
        addUser("cite", "cite", null, Collections.singletonList("ROLE_DUMMY"));
        addUser("cite_high", "cite", null, Collections.singletonList("ROLE_DUMMY"));
        addUser("cite_low", "cite", null, Collections.singletonList("ROLE_DUMMY"));

        FilterFactory ff = CommonFactoryFinder.getFilterFactory(null);
        TestResourceAccessManager tam =
                (TestResourceAccessManager) applicationContext.getBean("testResourceAccessManager");
        CoverageInfo raster = getCoverageInfo();

        // the watertemp mosaic holds two granules at elevation 0 and two at elevation 100
        tam.putLimits("cite_high", raster, limits(ff.equals(ff.property("elevation"), ff.literal(100))));
        tam.putLimits("cite_low", raster, limits(ff.equals(ff.property("elevation"), ff.literal(0))));
    }

    /** Only the read filter is set: the raster filter would clip pixels, not hide granules. */
    private CoverageAccessLimits limits(Filter readFilter) {
        return new CoverageAccessLimits(CatalogMode.HIDE, readFilter, null, null);
    }

    @Test
    public void testDomainValuesUnrestricted() {
        login("cite", "cite");
        testDomainsValuesRepresentation(DimensionsUtils.NO_LIMIT, "0", "100");
    }

    @Test
    public void testDomainValuesRestrictedToHighElevation() {
        login("cite_high", "cite");
        testDomainsValuesRepresentation(DimensionsUtils.NO_LIMIT, "100");
    }

    @Test
    public void testDomainValuesRestrictedToLowElevation() {
        login("cite_low", "cite");
        testDomainsValuesRepresentation(DimensionsUtils.NO_LIMIT, "0");
    }

    @Test
    public void testDescribeDomainsRestricted() throws Exception {
        Document result = describeDomains("cite_high");
        checkXpathCount(result, "/md:Domains/md:DimensionDomain[ows:Identifier='elevation']", "1");
        checkXpathCount(result, "/md:Domains/md:DimensionDomain[md:Domain='100']", "1");
        // the two granules at elevation 0 must not be counted
        checkXpathCount(result, "/md:Domains/md:DimensionDomain[ows:Identifier='elevation'][md:Size='1']", "1");
    }

    @Test
    public void testDescribeDomainsUnrestricted() throws Exception {
        Document result = describeDomains("cite");
        checkXpathCount(result, "/md:Domains/md:DimensionDomain[md:Domain='0,100']", "1");
    }

    /**
     * The histogram counts granules, so it must see the restricted set too: the hidden granules have to drop out of the
     * domain, not just move to another bucket.
     */
    @Test
    public void testHistogramRestricted() {
        login("cite_high", "cite");
        Dimension dimension = buildDimension(createDimension(true, null));
        Tuple<String, List<Integer>> histogram = dimension.getHistogram(Filter.INCLUDE, "50");
        assertThat(histogram.first, is("100.0/150.0/50.0"));
        assertThat(histogram.second, contains(2));
    }

    @Test
    public void testHistogramUnrestricted() {
        login("cite", "cite");
        Dimension dimension = buildDimension(createDimension(true, null));
        Tuple<String, List<Integer>> histogram = dimension.getHistogram(Filter.INCLUDE, "50");
        assertThat(histogram.first, is("0.0/150.0/50.0"));
        assertThat(histogram.second, contains(2, 0, 2));
    }

    /**
     * The request has to carry its own credentials: the filter chain authenticates it on its own, so a thread bound
     * login would be replaced by the anonymous user.
     */
    private Document describeDomains(String user) throws Exception {
        setRequestAuth(user, "cite");
        String queryRequest = "request=DescribeDomains&Version=1.0.0&Layer=%s&TileMatrixSet=EPSG:4326"
                .formatted(getLayerId(RASTER_ELEVATION));
        MockHttpServletResponse response = getAsServletResponse("gwc/service/wmts?" + queryRequest);
        return getResultAsDocument(response);
    }

    @Override
    protected Dimension buildDimension(DimensionInfo dimensionInfo) {
        return new RasterElevationDimension(wms, getLayerInfo(), dimensionInfo);
    }

    // getCatalog(), not the catalog field: the field is only set by the @Before, which runs after onSetUp
    private LayerInfo getLayerInfo() {
        return getCatalog().getLayerByName(RASTER_ELEVATION.getLocalPart());
    }

    private CoverageInfo getCoverageInfo() {
        return (CoverageInfo) getLayerInfo().getResource();
    }
}

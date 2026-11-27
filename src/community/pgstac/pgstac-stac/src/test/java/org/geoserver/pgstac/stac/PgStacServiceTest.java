/* (c) 2026 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.pgstac.stac;

import static org.geoserver.ogcapi.SwaggerJSONAPIMessageConverter.OPEN_API_MEDIA_TYPE_VALUE;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.Assert.assertEquals;

import com.jayway.jsonpath.DocumentContext;
import io.swagger.v3.core.util.Json;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.geoserver.ogcapi.OGCApiTestSupport;
import org.geoserver.pgstac.PgStacServiceInfo;
import org.geoserver.platform.Service;
import org.junit.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.bind.annotation.RequestMapping;

/** Checks the service registers with its default name and path. */
public class PgStacServiceTest extends OGCApiTestSupport {

    @Test
    public void testRegisteredAsService() {
        Service service = applicationContext.getBean("pgstacServiceFactory", Service.class);
        assertEquals("STAC", service.getId());
        assertEquals("../ogc/stac/v1", service.getCustomCapabilitiesLink());
    }

    @Test
    public void testLandingPage() throws Exception {
        DocumentContext json = getAsJSONPath("ogc/stac/v1", 200);
        assertEquals("Catalog", json.read("type"));
        assertEquals("1.0.0", json.read("stac_version"));
        assertEquals(PgStacService.CONFORMANCE, json.read("conformsTo"));
        assertThat(
                json.read("links[?(@.rel == 'data')].href"),
                contains("http://localhost:8080/geoserver/ogc/stac/v1/collections"));
    }

    @Test
    public void testConformance() throws Exception {
        DocumentContext json = getAsJSONPath("ogc/stac/v1/conformance", 200);
        assertEquals(PgStacService.CONFORMANCE, json.read("conformsTo"));
    }

    @Test
    public void testOpenAPI() throws Exception {
        PgStacServiceInfo info = getGeoServer().getService(PgStacServiceInfo.class);
        info.setDefaultPageSize(7);
        info.setMaxFeatures(70);
        getGeoServer().save(info);

        MockHttpServletResponse response = getAsMockHttpServletResponse("ogc/stac/v1/openapi", 200);
        assertThat(response.getContentType(), startsWith(OPEN_API_MEDIA_TYPE_VALUE));
        // the swagger models bind with Jackson 2, as in the OGC API Features ApiTest
        OpenAPI api = Json.mapper().readValue(response.getContentAsString(), OpenAPI.class);
        assertEquals(
                "http://localhost:8080/geoserver/ogc/stac/v1",
                api.getServers().get(0).getUrl());
        Schema<?> limit = api.getComponents().getParameters().get("limit").getSchema();
        assertEquals(Integer.valueOf(7), limit.getDefault());
        assertEquals(BigDecimal.valueOf(70), limit.getMaximum());
        assertEquals(mappedOperations(), operations(api));
    }

    @Test
    public void testServiceDescLink() throws Exception {
        DocumentContext json = getAsJSONPath("ogc/stac/v1", 200);
        assertThat(
                json.read("links[?(@.rel == 'service-desc')].href"),
                contains("http://localhost:8080/geoserver/ogc/stac/v1/openapi"));
    }

    /** The controller mappings as "METHOD /path name", the path as an OpenAPI template, without regular expressions. */
    private static Set<String> mappedOperations() {
        return Arrays.stream(PgStacService.class.getMethods())
                .map(m -> AnnotatedElementUtils.findMergedAnnotation(m, RequestMapping.class))
                .filter(Objects::nonNull)
                .map(PgStacServiceTest::operation)
                .collect(Collectors.toSet());
    }

    private static String operation(RequestMapping mapping) {
        String path = mapping.path().length == 0 ? "" : mapping.path()[0];
        return mapping.method()[0] + " /" + path.replaceAll(":[^}]*", "") + " " + mapping.name();
    }

    private static Set<String> operations(OpenAPI api) {
        Set<String> operations = new HashSet<>();
        api.getPaths().forEach((path, item) -> item.readOperationsMap()
                .forEach((method, op) -> operations.add(method + " " + path + " " + op.getOperationId())));
        return operations;
    }
}

/* (c) 2026 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.pgstac.rest;

import java.lang.reflect.Type;
import org.geoserver.config.GeoServer;
import org.geoserver.config.ServiceInfo;
import org.geoserver.config.util.XStreamPersister;
import org.geoserver.pgstac.PgStacServiceInfo;
import org.geoserver.rest.RestBaseController;
import org.geoserver.rest.converters.XStreamMessageConverter;
import org.geoserver.rest.service.ServiceSettingsController;
import org.geoserver.rest.util.MediaTypeExtensions;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The PgSTAC service settings, connection and access rules included; global only, as the service has no workspace
 * settings. The database password is written encrypted, and accepted either encrypted or in plain text.
 */
@RestController
@ControllerAdvice
@RequestMapping(
        path = RestBaseController.ROOT_PATH + "/services/stac",
        produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_XML_VALUE, MediaType.TEXT_HTML_VALUE})
public class PgStacSettingsController extends ServiceSettingsController<PgStacServiceInfo> {

    public PgStacSettingsController(GeoServer geoServer) {
        super(geoServer, PgStacServiceInfo.class);
    }

    /** Copies the members the body carries onto the current settings, leaving the others as they are. */
    @Override
    @PutMapping(
            value = "/settings",
            consumes = {
                MediaType.APPLICATION_JSON_VALUE,
                MediaTypeExtensions.TEXT_JSON_VALUE,
                MediaType.APPLICATION_XML_VALUE,
                MediaType.TEXT_XML_VALUE
            })
    public void serviceSettingsPut(
            @RequestBody PgStacServiceInfo info, @PathVariable(required = false) String workspaceName) {
        super.serviceSettingsPut(info, null);
    }

    @Override
    public String getTemplateName(Object object) {
        return "pgstacSettings";
    }

    @Override
    public boolean supports(
            MethodParameter methodParameter, Type targetType, Class<? extends HttpMessageConverter<?>> converterType) {
        return PgStacServiceInfo.class.isAssignableFrom(methodParameter.getParameterType());
    }

    @Override
    public void configurePersister(XStreamPersister persister, XStreamMessageConverter converter) {
        persister.setCallback(new XStreamPersister.Callback() {
            @Override
            protected ServiceInfo getServiceObject() {
                return geoServer.getService(PgStacServiceInfo.class);
            }

            @Override
            protected Class<PgStacServiceInfo> getObjectClass() {
                return PgStacServiceInfo.class;
            }
        });
    }
}

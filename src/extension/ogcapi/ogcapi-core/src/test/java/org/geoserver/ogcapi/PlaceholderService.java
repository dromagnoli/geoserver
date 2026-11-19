/* (c) 2026 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.ogcapi;

import org.geoserver.ogcapi.HelloService.HelloServiceInfo;
import org.geoserver.ogcapi.HelloService.HelloServiceInfoImpl;
import org.geoserver.ows.Dispatcher;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

/** A service whose name and paths are placeholders, set by {@link PlaceholderServiceTest}. */
@APIService(
        service = "${ogcapi.placeholder.service:Placeholder}",
        version = "1.0.0",
        landingPage = "${ogcapi.placeholder.path:ogc/placeholder/v1}",
        serviceClass = HelloServiceInfo.class)
@RequestMapping(path = "${ogcapi.placeholder.path:ogc/placeholder/v1}")
public class PlaceholderService {

    private final HelloServiceInfoImpl serviceInfo = new HelloServiceInfoImpl();

    public PlaceholderService() {
        serviceInfo.setName("Placeholder");
        serviceInfo.setEnabled(true);
    }

    @GetMapping(name = "landingPage")
    @ResponseBody
    public Message landingPage() {
        return new Message("Placeholder landing page");
    }

    /** Reports the service name and landing page GeoServer sees for the request. */
    @GetMapping(path = "info", name = "info")
    @ResponseBody
    public Message info() {
        String service = Dispatcher.REQUEST.get().getService();
        return new Message(service + " " + APIRequestInfo.get().getServiceLandingPage());
    }

    /** Used by the {@link org.geoserver.ows.DisabledServiceCheck} */
    public HelloServiceInfo getServiceInfo() {
        return serviceInfo;
    }
}

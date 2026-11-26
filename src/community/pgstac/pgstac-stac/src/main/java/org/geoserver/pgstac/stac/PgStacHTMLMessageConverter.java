/* (c) 2026 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.pgstac.stac;

import java.util.HashMap;
import java.util.Map;
import org.geoserver.config.GeoServer;
import org.geoserver.ogcapi.AnnotatedHTMLMessageConverter;
import org.geoserver.ogcapi.FreemarkerTemplateSupport;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Renders the JSON documents of the service as HTML: the templates get {@code model} as plain maps and lists, and
 * {@code modelJSON} as the JSON text, safe to embed in a {@code script} element.
 */
@Component
public class PgStacHTMLMessageConverter extends AnnotatedHTMLMessageConverter<JsonNode> {

    private static final JsonMapper MAPPER = new JsonMapper();

    public PgStacHTMLMessageConverter(FreemarkerTemplateSupport support, GeoServer geoServer) {
        super(support, geoServer);
    }

    @Override
    public boolean canWrite(Class<?> clazz, MediaType mediaType) {
        return JsonNode.class.isAssignableFrom(clazz) && super.canWrite(clazz, mediaType);
    }

    @Override
    protected HashMap<String, Object> setupModel(Object value) {
        HashMap<String, Object> model = super.setupModel(MAPPER.convertValue(value, Map.class));
        // "<" only occurs inside JSON strings, where the escape keeps a stored "</script>" from ending the element
        model.put("modelJSON", value.toString().replace("<", "\\u003c"));
        return model;
    }

    @Override
    public int getPriority() {
        // ahead of the generic converter, which would hand the templates the Jackson tree
        return super.getPriority() - 1;
    }
}

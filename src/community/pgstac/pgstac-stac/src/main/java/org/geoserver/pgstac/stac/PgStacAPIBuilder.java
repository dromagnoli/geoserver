/* (c) 2026 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.pgstac.stac;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import java.io.IOException;
import java.math.BigDecimal;
import org.geoserver.ogcapi.OpenAPIBuilder;
import org.geoserver.pgstac.PgStacServiceInfo;

/** Builds the OpenAPI document from the {@code openapi.yaml} template, with the page sizes of the service. */
class PgStacAPIBuilder extends OpenAPIBuilder<PgStacServiceInfo> {

    PgStacAPIBuilder() {
        super(PgStacAPIBuilder.class, "openapi.yaml", "STAC API", PgStacService.class);
    }

    @Override
    public OpenAPI build(PgStacServiceInfo service) throws IOException {
        OpenAPI api = super.build(service);
        Schema<?> limit = api.getComponents().getParameters().get("limit").getSchema();
        limit.setDefault(Math.min(service.getDefaultPageSize(), service.getMaxFeatures()));
        limit.setMaximum(BigDecimal.valueOf(service.getMaxFeatures()));
        return api;
    }

    @Override
    protected void addBasePathFormats(OpenAPI api) {
        // the template declares the formats: the base class would list those of its landing page document,
        // while this service returns plain JSON trees
    }
}

/* (c) 2026 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.pgstac;

import org.geoserver.config.GeoServer;
import org.geoserver.config.util.XStreamServiceLoader;
import org.geoserver.platform.GeoServerResourceLoader;
import org.springframework.stereotype.Component;

/** Bootstraps the default {@link PgStacServiceInfo} and loads/saves it under {@code data_dir/pgstac}. */
@Component
public class PgStacXStreamLoader extends XStreamServiceLoader<PgStacServiceInfo> {

    public PgStacXStreamLoader(GeoServerResourceLoader resourceLoader) {
        super(resourceLoader, "pgstac");
    }

    @Override
    protected PgStacServiceInfo createServiceFromScratch(GeoServer gs) {
        PgStacServiceInfoImpl info = new PgStacServiceInfoImpl();
        info.setName("STAC");
        info.setTitle("PgSTAC");
        info.setAbstract("STAC API backed by a PgSTAC PostgreSQL database.");
        return info;
    }

    @Override
    public Class<PgStacServiceInfo> getServiceClass() {
        return PgStacServiceInfo.class;
    }
}

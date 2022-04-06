/* (c) 2020 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.libdeflate;

import org.geoserver.catalog.MetadataMap;
import org.geoserver.config.GeoServer;
import org.geoserver.config.GeoServerInfo;
import org.geoserver.config.GeoServerInitializer;

/** Simple initializer to populate the Libdeflate settings on first usage */
public class LibdeflateSettingsInitializer implements GeoServerInitializer {

    @Override
    public void initialize(GeoServer geoServer) throws Exception {
        // Add a new Element to the metadata map
        GeoServerInfo global = geoServer.getGlobal();
        MetadataMap metadata = global.getSettings().getMetadata();
        if (!metadata.containsKey(LibdeflateSettings.LIBDEFLATE_SETTINGS_KEY)) {
            metadata.put(LibdeflateSettings.LIBDEFLATE_SETTINGS_KEY, new LibdeflateSettings());
            geoServer.save(global);
        }
    }
}

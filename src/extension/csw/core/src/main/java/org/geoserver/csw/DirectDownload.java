/* (c) 2015 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.csw;

import java.io.File;
import java.util.logging.Logger;

import org.geoserver.csw.store.CatalogStore;
import org.geotools.util.logging.Logging;

/**
 * Runs the DirectDownload request
 * 
 * @author Daniele Romagnoli - GeoSolutions
 */
public class DirectDownload {

    static final Logger LOGGER = Logging.getLogger(DirectDownload.class);

    CSWInfo csw;

    CatalogStore store;

    public DirectDownload(CSWInfo csw, CatalogStore store) {
        this.csw = csw;
        this.store = store;
    }

    File run(String layerId, String contentPath) {
        // store.

        return null;

    }

}

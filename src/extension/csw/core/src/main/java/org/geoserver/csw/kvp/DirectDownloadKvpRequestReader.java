/* (c) 2015 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */

package org.geoserver.csw.kvp;

import java.util.List;
import java.util.Map;

import org.geoserver.csw.DirectDownloadType;
import org.geoserver.ows.KvpRequestReader;
import org.geoserver.platform.ServiceException;

/**
 * DirectDownload KVP request reader
 * 
 * @author Daniele Romagnoli, GeoSolutions SAS
 */
public class DirectDownloadKvpRequestReader extends KvpRequestReader {

    private final static String RESOURCE_ID = "resourceId"; 
    public DirectDownloadKvpRequestReader() {
        super(DirectDownloadType.class);
    }

    @Override
    public Object read(Object req, Map kvp, Map rawKvp) throws Exception {

        // Force the ResourceID element to be a simple String instead of an array
        kvp.put(RESOURCE_ID, ((List)kvp.get(RESOURCE_ID)).get(0));
        DirectDownloadType request = (DirectDownloadType) super.read(req, kvp, rawKvp);

        if (request.getResourceId() == null) {
            throw new ServiceException(
                    "resourceId parameter not provided for DirectDownload operation",
                    ServiceException.MISSING_PARAMETER_VALUE, "resourceId");
        }

        return request;
    }

}

/* (c) 2015 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */

package org.geoserver.csw.kvp;

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

    public DirectDownloadKvpRequestReader() {
        super(DirectDownloadType.class);
    }

    @Override
    public Object read(Object req, Map kvp, Map rawKvp) throws Exception {

        DirectDownloadType request = (DirectDownloadType) super.read(req, kvp, rawKvp);

        if (request.getResourceId() == null) {
            throw new ServiceException(
                    "resourceId parameter not provided for DirectDownload operation",
                    ServiceException.MISSING_PARAMETER_VALUE, "resourceId");
        }

        return request;
    }

}

/* (c) 2014 Open Source Geospatial Foundation - all rights reserved
 * (c) 2001 - 2013 OpenPlans
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */

package org.geoserver.csw;

import org.geoserver.config.ServiceInfo;

/**
 * CSW configuration
 * 
 * @author Andrea Aime - GeoSolutions
 */
public interface CSWInfo extends ServiceInfo {

    /**
     * Get the flag that determines the encoding of the CSW schemaLocation.
     *  
     * True if the CSW schemaLocation should refer to the canonical location,
     * false if the CSW schemaLocation should refer to a copy served by GeoServer.
     */
    boolean isCanonicalSchemaLocation();

    /**
     * Set the flag that determines the encoding of the CSW schemaLocation. 
     * True if the CSW schemaLocation should refer to the canonical location,
     * false if the CSW schemaLocation should refer to a copy served by GeoServer.
     */
    void setCanonicalSchemaLocation(boolean canonicalSchemaLocation);

    /**
     * Returns the maximum allowed download size, in kilobytes, for a single download.
     * The check will be performed on the raw data files associated with a download link.
     * This won't take into account any potential compression used after the size is 
     * computed.
     */
    long getMaxDownloadSize();

    /**
     * Set the maximum allowed download size, in kilobytes, for a single download.
     */
    void setMaxDownloadSize(long size);
    
    /**
     * Get the flag that determines if directDownload links should be created.
     *  
     * True if by default, directDownload links should be created.
     * false otherwise.
     */
    boolean isDirectDownloadLinksEnabled();

    /**
     * Set the flag that determines if directDownload links should be created.
     *  
     * True if by default, directDownload links should be created.
     * false otherwise.
     */
    void setDirectDownloadLinksEnabled(boolean isDirectDownloadLinksEnabled);
    
}

/* (c) 2014 - 2015 Open Source Geospatial Foundation - all rights reserved
 * (c) 2001 - 2013 OpenPlans
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */

package org.geoserver.csw;

import org.geoserver.config.impl.ServiceInfoImpl;

/**
 * CSW information implementation
 * 
 * @author Andrea Aime, GeoSolutions
 */
@SuppressWarnings("unchecked")
public class CSWInfoImpl extends ServiceInfoImpl implements CSWInfo {

    /**
     * 
     */
    private static final long serialVersionUID = -986573241436434750L;
    
    boolean canonicalSchemaLocation;
    
    boolean directDownloadLinksEnabled;

    private long maxDownloadSize;
    
    public CSWInfoImpl(){
        
    }

    @Override
    public boolean isCanonicalSchemaLocation() {
        return canonicalSchemaLocation;
    }

    @Override
    public void setCanonicalSchemaLocation(boolean canonicalSchemaLocation) {
        this.canonicalSchemaLocation = canonicalSchemaLocation;
    }

    @Override
    public long getMaxDownloadSize() {
        return maxDownloadSize;
    }

    @Override
    public void setMaxDownloadSize(long maxDownloadSize) {
        this.maxDownloadSize = maxDownloadSize;
    }

    @Override
    public boolean isDirectDownloadLinksEnabled() {
        return directDownloadLinksEnabled;
    }

    @Override
    public void setDirectDownloadLinksEnabled(boolean directDownloadLinksEnabled) {
        this.directDownloadLinksEnabled = directDownloadLinksEnabled;
        
    }

}

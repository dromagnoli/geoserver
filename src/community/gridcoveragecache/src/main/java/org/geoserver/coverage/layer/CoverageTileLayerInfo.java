/* (c) 2014 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.coverage.layer;

import org.geoserver.gwc.layer.GeoServerTileLayerInfo;

public interface CoverageTileLayerInfo extends GeoServerTileLayerInfo {
    
    public enum SeedingPolicy{
        DIRECT, RECURSIVE, BASE_LEVEL;
    }

//    public void setEnabledCaching(boolean enabledCaching);
//    
//    public boolean isEnabledCaching();
    
    //public void setResamplingAlgorithm(Interpolation resamplingAlgorithm);
    
    //public Interpolation getResamplingAlgorithm();
    
    //public void setSeedingPolicy(SeedingPolicy seedingPolicy);
    
    //public SeedingPolicy getSeedingPolicy();
}

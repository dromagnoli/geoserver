package org.geoserver.coverage;

import javax.media.jai.Interpolation;

import org.geoserver.gwc.layer.GeoServerTileLayerInfo;

public interface WCSLayerInfo extends GeoServerTileLayerInfo {
    
    public enum SeedingPolicy{
        DIRECT, RECURSIVE, BASE_LEVEL;
    }

//    public void setEnabledCaching(boolean enabledCaching);
//    
//    public boolean isEnabledCaching();
    
    public void setResamplingAlgorithm(Interpolation resamplingAlgorithm);
    
    public Interpolation getResamplingAlgorithm();
    
    public void setSeedingPolicy(SeedingPolicy seedingPolicy);
    
    public SeedingPolicy getSeedingPolicy();
}

package org.geoserver.coverage;

import javax.media.jai.Interpolation;

import org.geoserver.gwc.layer.GeoServerTileLayerInfo;

public interface WCSLayerInfo extends GeoServerTileLayerInfo {

    public void setEnabledCaching(boolean enabledCaching);
    
    public boolean isEnabledCaching();
    
    public void setResamplingAlgorithm(Interpolation resamplingAlgorithm);
    
    public Interpolation getResamplingAlgorithm();
    
    public void setSeedingPolicy(String seedingPolicy);
    
    public String getSeedingPolicy();
}

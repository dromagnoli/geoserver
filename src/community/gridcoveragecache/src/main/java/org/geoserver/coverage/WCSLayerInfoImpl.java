package org.geoserver.coverage;

import java.io.Serializable;

import javax.media.jai.Interpolation;

import org.geoserver.gwc.layer.GeoServerTileLayerInfo;
import org.geoserver.gwc.layer.GeoServerTileLayerInfoImpl;

public class WCSLayerInfoImpl extends GeoServerTileLayerInfoImpl implements WCSLayerInfo,Serializable {

    public WCSLayerInfoImpl() {
        super();
    }

    public WCSLayerInfoImpl(GeoServerTileLayerInfo info) {
        super();
        setEnabled(info.isEnabled());
        setGutter(info.getGutter());
        setMetaTilingX(info.getMetaTilingX());
        setMetaTilingY(info.getMetaTilingY());
        setParameterFilters(info.getParameterFilters());
        setGridSubsets(info.getGridSubsets());
        if(info instanceof WCSLayerInfoImpl){
            setResamplingAlgorithm(((WCSLayerInfoImpl) info).getResamplingAlgorithm());
            setSeedingPolicy(((WCSLayerInfoImpl) info).getSeedingPolicy());
        }
    }

    /** serialVersionUID */
    private static final long serialVersionUID = 1L;

//    private boolean enabledCaching;

    private Interpolation resamplingAlgorithm = Interpolation
            .getInstance(Interpolation.INTERP_NEAREST);
//    private String resamplingAlgorithm = "nearest";
//    
//    private String seedingPolicy = "direct";

    private SeedingPolicy seedingPolicy = SeedingPolicy.DIRECT;

    @Override
    public void setResamplingAlgorithm(Interpolation resamplingAlgorithm) {
        this.resamplingAlgorithm = resamplingAlgorithm;
    }

    @Override
    public Interpolation getResamplingAlgorithm() {
        return resamplingAlgorithm;
    }
//
//    @Override
//    public void setEnabledCaching(boolean enabledCaching) {
//        this.enabledCaching = enabledCaching;
//    }
//
//    @Override
//    public boolean isEnabledCaching() {
//        return enabledCaching;
//    }

    @Override
    public void setSeedingPolicy(SeedingPolicy seedingPolicy) {
        this.seedingPolicy = seedingPolicy;
    }

    @Override
    public SeedingPolicy getSeedingPolicy() {
        return seedingPolicy;
    }
    
    @Override
    public GeoServerTileLayerInfoImpl clone() {
        GeoServerTileLayerInfoImpl info = super.clone();
        if(info instanceof WCSLayerInfoImpl){
            return info;
        } else {
            WCSLayerInfoImpl infoImpl = new WCSLayerInfoImpl(info);
            infoImpl.setSeedingPolicy(SeedingPolicy.DIRECT);
            infoImpl.setResamplingAlgorithm(Interpolation
            .getInstance(Interpolation.INTERP_NEAREST));
            return infoImpl;
        }
    }
}

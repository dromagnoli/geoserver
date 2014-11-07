/* (c) 2014 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.coverage.layer;

import java.io.Serializable;

import javax.media.jai.Interpolation;

import org.geoserver.gwc.layer.GeoServerTileLayerInfo;
import org.geoserver.gwc.layer.GeoServerTileLayerInfoImpl;

public class CoverageTileLayerInfoImpl extends GeoServerTileLayerInfoImpl implements CoverageTileLayerInfo,Serializable {

    public CoverageTileLayerInfoImpl() {
        super();
    }

    public CoverageTileLayerInfoImpl(GeoServerTileLayerInfo info) {
        super();
        setEnabled(info.isEnabled());
        setGutter(info.getGutter());
        setMetaTilingX(info.getMetaTilingX());
        setMetaTilingY(info.getMetaTilingY());
        setParameterFilters(info.getParameterFilters());
        setGridSubsets(info.getGridSubsets());
        if(info instanceof CoverageTileLayerInfoImpl){
            setResamplingAlgorithm(((CoverageTileLayerInfoImpl) info).getResamplingAlgorithm());
            setSeedingPolicy(((CoverageTileLayerInfoImpl) info).getSeedingPolicy());
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
        if(info instanceof CoverageTileLayerInfoImpl){
            return info;
        } else {
            CoverageTileLayerInfoImpl infoImpl = new CoverageTileLayerInfoImpl(info);
            infoImpl.setSeedingPolicy(SeedingPolicy.DIRECT);
            infoImpl.setResamplingAlgorithm(Interpolation
            .getInstance(Interpolation.INTERP_NEAREST));
            return infoImpl;
        }
    }
}

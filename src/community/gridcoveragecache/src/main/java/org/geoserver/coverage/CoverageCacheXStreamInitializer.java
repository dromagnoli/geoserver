package org.geoserver.coverage;
/* Copyright (c) 2014 OpenPlans - www.openplans.org. All rights reserved.
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */


import org.geoserver.config.util.XStreamPersister;
import org.geoserver.config.util.XStreamPersisterInitializer;
import org.geoserver.coverage.layer.CoverageTileLayerInfoImpl;

/**
 * 
 * Implementation of XStreamPersisterInitializer extension point to serialize CoverageTileLayer
 *
 */
public class CoverageCacheXStreamInitializer implements XStreamPersisterInitializer {

    @Override
    public void init(XStreamPersister persister) {
        persister.registerBreifMapComplexType("coverageTileLayerInfo",CoverageTileLayerInfoImpl.class);
    }
}

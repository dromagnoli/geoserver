/* (c) 2015 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.csw;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

import org.geoserver.catalog.CoverageInfo;
import org.geoserver.config.GeoServer;
import org.geoserver.csw.store.CatalogStore;
import org.geoserver.csw.store.internal.DownloadLinkHandler;
import org.geoserver.platform.GeoServerExtensions;
import org.geoserver.platform.ServiceException;
import org.geotools.coverage.grid.io.GranuleSource;
import org.geotools.coverage.grid.io.GridCoverage2DReader;
import org.geotools.coverage.grid.io.StructuredGridCoverage2DReader;
import org.geotools.data.CloseableIterator;
import org.geotools.data.DataUtilities;
import org.geotools.data.FileGroup;
import org.geotools.data.FileResourceInfo;
import org.geotools.data.Query;
import org.geotools.data.ResourceInfo;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.factory.GeoTools;
import org.geotools.feature.NameImpl;
import org.geotools.resources.coverage.FeatureUtilities;
import org.geotools.util.logging.Logging;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.type.Name;
import org.opengis.filter.FilterFactory2;

/**
 * Runs the DirectDownload request
 * 
 * @author Daniele Romagnoli - GeoSolutions
 */
public class DirectDownload {

    FilterFactory2 ff = FeatureUtilities.DEFAULT_FILTER_FACTORY;

    static final Logger LOGGER = Logging.getLogger(DirectDownload.class);

    CSWInfo csw;

    CatalogStore store;

    GeoServer geoserver;

    DownloadLinkHandler handler;

    public DirectDownload(CSWInfo csw, CatalogStore store) {
        this.csw = csw;
        this.store = store;
        this.geoserver = csw.getGeoServer();
        this.handler = GeoServerExtensions.bean(DownloadLinkHandler.class);
    }

    /**
     * Prepare the list of files to be downloaded from the current request.
     * @param request
     * @return
     */
    public List<File> run(DirectDownloadType request) {
        String resourceId = request.getResourceId();

        // Extract namespace, layername and fileId from the resourceId
        String [] identifiers = resourceId.split(":");
        String nameSpace = identifiers[0];
        String layerName = identifiers[1];

        // SHA-1 are 20 bytes in lenght
        String fileName = identifiers[2].substring(20);
        assert(identifiers.length == 3);
        Name coverageName = new NameImpl(nameSpace, layerName);

        // Get the underlying coverage from the catalog
        CoverageInfo info = geoserver.getCatalog().getCoverageByName(coverageName);
        if (info == null) {
            throw new ServiceException("No object available for the specified name:" + coverageName);
        }
        
        // Get the reader to access the coverage
        GridCoverage2DReader reader;
        try {
            reader = (GridCoverage2DReader) info.getGridCoverageReader(null, GeoTools.getDefaultHints());
        } catch (IOException e) {
            throw new ServiceException("Failed to get a reader for the associated info: " + info, e);
        }
        String nativeCoverageName = info.getNativeCoverageName();
        ResourceInfo resourceInfo = reader.getInfo(nativeCoverageName);
        if (resourceInfo instanceof FileResourceInfo){
            FileResourceInfo fileResourceInfo = (FileResourceInfo) resourceInfo;

            // Get the resource files 
            CloseableIterator<FileGroup> files = fileResourceInfo.getFiles();

            List<File> returnedFiles = new ArrayList<File>();
            if (reader instanceof StructuredGridCoverage2DReader) {
                try {
                    GranuleSource source = ((StructuredGridCoverage2DReader) reader).getGranules(nativeCoverageName, false);
                    Query query = new Query();

                    // TODO: Make sure to use the proper location attribute
//                    handler.hashFile(mainFile)
                    
                    
                    
                } catch (UnsupportedOperationException e) {
                    throw new ServiceException("Exception occurred while getting the granules for the specified coverage:" + nativeCoverageName, e);
                } catch (IOException e) {
                    throw new ServiceException("Exception occurred while getting the granules for the specified coverage:" + nativeCoverageName,e);
                }
            } else {
                // Simple reader. No way to retrieve the originating files. Return
                // them all since they will be the only ones needed.
                while (files.hasNext()) {
                    FileGroup fileGroup = files.next();
                    returnedFiles.add(fileGroup.getMainFile());
                    List<File> supportFile = fileGroup.getSupportFiles(); 
                    if (supportFile != null && !supportFile.isEmpty()) {
                        returnedFiles.addAll(supportFile);
                    }
                }
            }
        } else {
            throw new ServiceException("Unable to get files from the specified ResourceInfo which"
                    + " doesn't implement FileResourceInfo");
        }
        
        
        
        return Collections.singletonList(new File("c:\\data\\sr.properties"));

    }

}

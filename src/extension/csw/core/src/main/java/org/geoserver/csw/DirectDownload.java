/* (c) 2015 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.csw;

import java.io.File;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
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
import org.geotools.data.FileGroupProvider;
import org.geotools.data.FileGroupProvider.FileGroup;
import org.geotools.data.FileResourceInfo;
import org.geotools.data.Query;
import org.geotools.data.ResourceInfo;
import org.geotools.factory.GeoTools;
import org.geotools.feature.NameImpl;
import org.geotools.resources.coverage.FeatureUtilities;
import org.geotools.util.logging.Logging;
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
        List<File> returnedFiles = new ArrayList<File>();
        String resourceId = request.getResourceId();

        // Extract namespace, layername and fileId from the resourceId
        String [] identifiers = resourceId.split(":");
        String nameSpace = identifiers[0];
        String layerName = identifiers[1];

        // SHA-1 are 20 bytes in length
        String hash = identifiers[2];
        String fileName = hash.substring(41);
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
            FileGroupProvider fileGroupProvider = fileResourceInfo.getFiles();
            if (reader instanceof StructuredGridCoverage2DReader) {
                try {
                    Query query = new Query();
                    query.setFilter(ff.like(ff.property("location"),fileName));
                    CloseableIterator<FileGroup> files = fileGroupProvider.getFiles(query);
                    while (files.hasNext()) {
                        FileGroup fileGroup = files.next();
                        File mainFile = fileGroup.getMainFile();
                        String hashedName = handler.hashFile(mainFile);
                        if (hash.equalsIgnoreCase(hashedName)) {
                            returnedFiles.add(mainFile);
                            List<File> supportFile = fileGroup.getSupportFiles(); 
                            if (supportFile != null && !supportFile.isEmpty()) {
                                returnedFiles.addAll(supportFile);
                            }
                        }
                    }
                } catch (UnsupportedOperationException e) {
                    throw new ServiceException("Exception occurred while looking for the specified file from the original store:" + fileName, e);
                } catch (NoSuchAlgorithmException e) {
                    throw new ServiceException("Exception occurred while looking for the specified file from the original store:" + fileName, e);                } catch (IOException e) {
                        throw new ServiceException("Exception occurred while looking for the specified file from the original store:" + fileName, e);                } 
            } else {
                // Simple reader case. only 1 main file available
                CloseableIterator<FileGroup> files = fileGroupProvider.getFiles(null);
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
        
        return returnedFiles;

    }

}

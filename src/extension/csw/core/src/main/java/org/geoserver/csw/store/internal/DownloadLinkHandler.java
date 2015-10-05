/* (c) 2015 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.csw.store.internal;

import java.io.File;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.io.FilenameUtils;
import org.geoserver.catalog.CatalogInfo;
import org.geoserver.catalog.CoverageInfo;
import org.geoserver.ows.Dispatcher;
import org.geoserver.ows.Request;
import org.geoserver.ows.URLMangler.URLType;
import org.geoserver.ows.util.ResponseUtils;
import org.geotools.coverage.grid.io.GridCoverage2DReader;
import org.geotools.data.CloseableIterator;
import org.geotools.data.FileGroupProvider.FileGroup;
import org.geotools.data.FileResourceInfo;
import org.geotools.data.ResourceInfo;
import org.geotools.factory.GeoTools;
import org.geotools.gce.imagemosaic.Utils;
import org.geotools.util.DateRange;
import org.geotools.util.NumberRange;
import org.geotools.util.Range;
import org.geotools.util.logging.Logging;

/**
 * Class delegated to setup direct download links for a {@link CatalogInfo} 
 * instance.
 */
public class DownloadLinkHandler {

    public final static String RESOURCE_ID_PARAMETER = "resourceId";
    public final static String FILE_PARAMETER = "file"; 
    public final static String FILE_TEMPLATE = "${" + FILE_PARAMETER + "}";

    static final Logger LOGGER = Logging.getLogger(DownloadLinkHandler.class);

    /** An implementation of {@link CloseableIterator} for links creation */
    class CloseableLinksIterator implements CloseableIterator<String> {

        private static final String DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'";
        
        private SimpleDateFormat dateFormat = null;

        public CloseableLinksIterator(String baseLink, CloseableIterator<FileGroup> dataIterator) {
            this.dataIterator = dataIterator;
            this.baseLink = baseLink;
        }

        /** The base link to be updated with current file */ 
        private String baseLink;

        /** The underlying iterator providing files */
        private CloseableIterator<FileGroup> dataIterator;

        @Override
        public boolean hasNext() {
            return dataIterator.hasNext();
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException("Remove operation isn't supported");
        }

        @Override
        public String next() {
            // Get the file from the underlying iterator
            FileGroup element = dataIterator.next();
            File mainFile = element.getMainFile();
            String canonicalPath = null;
            try {
                canonicalPath = mainFile.getCanonicalPath();

                // Hash the file and setup the download link
                String hashFile = hashFile(mainFile);
                StringBuilder builder = new StringBuilder(baseLink.replace(FILE_TEMPLATE, hashFile));
                Map<String, Object> metadata = element.getMetadata();
                if (metadata != null && !metadata.isEmpty()) {
                    
                    Set<String> keys = metadata.keySet();
                    // Set time and elevation as first elements in the link
                    if (keys.contains(Utils.TIME_DOMAIN)) {
                        Object time = metadata.get(Utils.TIME_DOMAIN);
                        appendRangeToLink(Utils.TIME_DOMAIN, time, builder);
                    }                    
                    if (keys.contains(Utils.ELEVATION_DOMAIN)) {
                        Object elevation = metadata.get(Utils.ELEVATION_DOMAIN);
                        appendRangeToLink(Utils.ELEVATION_DOMAIN, elevation, builder);
                    }
                    for (String key: keys) {
                        if (!Utils.TIME_DOMAIN.equalsIgnoreCase(key) && !Utils.ELEVATION_DOMAIN.equalsIgnoreCase(key)) {
                            Object additional = metadata.get(key);
                            appendRangeToLink(key, additional, builder);
                        }
                    }
                }
                return builder.toString();
            } catch (IOException e) {
                throw new RuntimeException("Unable to encode the specified file:" + canonicalPath,
                        e.getCause());
            } catch (NoSuchAlgorithmException e) {
                throw new RuntimeException("Unable to encode the specified file:" + canonicalPath,
                        e.getCause());
            }
        }

        private void appendRangeToLink(String key, Object domain, StringBuilder builder) {
            String value = null;
            builder.append("&").append(key).append("=");
            if (domain instanceof DateRange) {
                // instantiate a new DateFormat instead of using a static one since
                // it's not thread safe
                if (dateFormat == null) {
                    dateFormat = new SimpleDateFormat(DATE_FORMAT);
                    dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
                }
                DateRange dateRange = (DateRange) domain;
                builder.append(dateFormat.format(dateRange.getMinValue()))
                .append("/").append(dateFormat.format(dateRange.getMaxValue()));
            } else if (domain instanceof NumberRange) {
                NumberRange numberRange = (NumberRange) domain;
                builder.append(numberRange.getMinValue())
                .append("/").append(numberRange.getMaxValue());
            } else if (domain instanceof Range) {
                // Generic range
                Range range = (Range) domain;
                builder.append(range.getMinValue())
                .append("/").append(range.getMaxValue());
            } else {
                throw new IllegalArgumentException("Domain " + domain + " isn't supported");
            }
        }

        @Override
        public void close() throws IOException {
            dataIterator.close();
        }
    }

    /** Template download link to be updated with actual values */
    private static String LINK = "ows?service=CSW&version=${version}&request="
            + "DirectDownload&" + RESOURCE_ID_PARAMETER + "=${nameSpace}:${layerName}&"
            + FILE_PARAMETER + "=" + FILE_TEMPLATE;

    /**
     * Generate download links for the specified info object.
     * 
     * @param info
     * @return
     */
    public Iterator<String> generateDownloadLinks(CatalogInfo info) {
        Request request = Dispatcher.REQUEST.get();
        String baseURL = null;

        // Retrieve the baseURL (something like: http://host:port/geoserver/...)
        try {
            if (baseURL == null) {
                baseURL = ResponseUtils.baseURL(request.getHttpRequest());
            }

            baseURL = ResponseUtils.buildURL(baseURL, "/", null, URLType.SERVICE);
        } catch (Exception e) {
        }
        baseURL += LINK;
        baseURL = baseURL.replace("${version}", request.getVersion());

        if (info instanceof CoverageInfo) {
            return linksFromCoverage(baseURL, (CoverageInfo) info);
        } else {
            if (LOGGER.isLoggable(Level.WARNING)) {
                LOGGER.warning("Download link for vectors isn't currently supported yet."
                        + " Returning null");
            }
        }
        return null;
    }

    /**
     * Return an {@link Iterator} containing {@link String}s representing
     * the downloadLinks associated to the provided {@link CoverageInfo} object.
     *
     * @param baseURL
     * @param coverageInfo
     * @return
     */
    private Iterator<String> linksFromCoverage(String baseURL, CoverageInfo coverageInfo) {
        GridCoverage2DReader reader;
        try {
            reader = (GridCoverage2DReader) coverageInfo.getGridCoverageReader(null,
                    GeoTools.getDefaultHints());
            ResourceInfo resourceInfo = reader.getInfo(coverageInfo.getNativeCoverageName());
            if (resourceInfo instanceof FileResourceInfo) {
                FileResourceInfo fileResourceInfo = (FileResourceInfo) resourceInfo;

                // Replace the template URL with proper values
                String baseLink = baseURL
                        .replace("${nameSpace}", coverageInfo.getNamespace().getName())
                        .replace("${layerName}", coverageInfo.getName());

                CloseableIterator<org.geotools.data.FileGroupProvider.FileGroup> dataIterator = fileResourceInfo
                        .getFiles().getFiles(null);
                return new CloseableLinksIterator(baseLink, dataIterator);

            } else {
                throw new RuntimeException("Donwload links handler need to provide "
                        + "download links to files. The ResourceInfo associated with the store should be a FileResourceInfo instance");
            }
        } catch (IOException e) {
            throw new RuntimeException("Unable to generate download links", e.getCause());
        }
    }

    /**
     * Return a SHA-1 based hash for the specified file, by appending the file's base name to the hashed full path. This allows to hide the underlying
     * file system structure.
     */
    public String hashFile(File mainFile) throws IOException, NoSuchAlgorithmException {
        String canonicalPath = mainFile.getCanonicalPath();
        String mainFilePath = FilenameUtils.getPath(canonicalPath);

        MessageDigest md = MessageDigest.getInstance("SHA-1");
        md.update(mainFilePath.getBytes());
        return Hex.encodeHexString(md.digest()) + "-" + mainFile.getName();
    }

    /**
     * Given a file download link, extract the link with no file references, used to 
     * request the full layer download.
     * 
     * @return
     */
    public String extractFullDownloadLink(String link) {
        int resourceIdIndex = link.indexOf(RESOURCE_ID_PARAMETER);
        int nextParamIndex = link.indexOf("&" + FILE_PARAMETER, resourceIdIndex);
        return link.substring(0, nextParamIndex);
    }
}

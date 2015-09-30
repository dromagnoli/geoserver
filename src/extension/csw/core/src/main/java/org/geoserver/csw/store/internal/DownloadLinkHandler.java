package org.geoserver.csw.store.internal;

import java.io.File;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Iterator;

import org.apache.commons.io.FilenameUtils;
import org.geoserver.catalog.CatalogInfo;
import org.geoserver.catalog.CoverageInfo;
import org.geoserver.ows.Dispatcher;
import org.geoserver.ows.Request;
import org.geoserver.ows.URLMangler.URLType;
import org.geoserver.ows.util.RequestUtils;
import org.geoserver.ows.util.ResponseUtils;
import org.geotools.coverage.grid.io.GridCoverage2DReader;
import org.geotools.data.CloseableIterator;
import org.geotools.data.FileGroup;
import org.geotools.data.FileResourceInfo;
import org.geotools.data.ResourceInfo;
import org.geotools.factory.GeoTools;
import org.geotools.resources.coverage.FeatureUtilities;
import org.opengis.filter.FilterFactory2;

public class DownloadLinkHandler {

    FilterFactory2 ff = FeatureUtilities.DEFAULT_FILTER_FACTORY;
    
    class CloseableLinksIterator implements CloseableIterator<String> {

        public CloseableLinksIterator(String baseLink, CloseableIterator<FileGroup> dataIterator) {
            this.dataIterator = dataIterator;
            this.baseLink = baseLink;
        }

        String baseLink;
        CloseableIterator<FileGroup> dataIterator;

        @Override
        public boolean hasNext() {
            return dataIterator.hasNext();
        }

        @Override
        public String next() {
            FileGroup element = dataIterator.next();
            File mainFile = element.getMainFile();
            String canonicalPath = null;
            try {
                canonicalPath = mainFile.getCanonicalPath();
                String mainFilePath = FilenameUtils.getPath(canonicalPath);
                String hashFile = hashFile(mainFile);
                
                return baseLink.replace("${file}", hashFile); 
            } catch (IOException e) {
                throw new RuntimeException("Unable to encode the specified file:" + canonicalPath,
                        e.getCause());
            } catch (NoSuchAlgorithmException e) {
                throw new RuntimeException("Unable to encode the specified file:" + canonicalPath,
                        e.getCause());
            }
        }

        @Override
        public void close() throws IOException {
            dataIterator.close();
        }
    }

    private static String LINK = "ows?service=CSW&version=${version}&request=DirectDownload&resourceId=${nameSpace}:${layerName}:${file}";

    /**
     * Generate download links for the specified info object.
     * @param info
     * @return
     */
    public Iterator<String> generateDownloadLinks(CatalogInfo info) {
        Request request = Dispatcher.REQUEST.get();
        String baseURL = null;
        
        try {
            if (baseURL == null) {
                baseURL = RequestUtils.baseURL(request.getHttpRequest());
            }
            
            baseURL = ResponseUtils.buildURL(baseURL, "/", null, URLType.SERVICE);
        } catch (Exception e) {
        }
        baseURL += LINK;
        
        if (info instanceof CoverageInfo) {
            CoverageInfo coverageInfo = ((CoverageInfo) info);
            GridCoverage2DReader reader;
            try {
                reader = (GridCoverage2DReader) coverageInfo.getGridCoverageReader(null,
                        GeoTools.getDefaultHints());
                ResourceInfo resourceInfo = reader.getInfo(coverageInfo.getNativeCoverageName());
                if (resourceInfo instanceof FileResourceInfo) {
                    resourceInfo = (FileResourceInfo) resourceInfo;
                    String baseLink = baseURL
                            .replace("${nameSpace}", coverageInfo.getNamespace().getName())
                            .replace("${layerName}", coverageInfo.getName())
                            .replace("${version}", request.getVersion());
                    return new CloseableLinksIterator(baseLink, ((FileResourceInfo) resourceInfo).getFiles());

                }
            } catch (IOException e) {
                throw new RuntimeException("Unable to generate download links", e.getCause());
            }
        }
        return null;
    }

    /** 
     * Return a SHA-1 based hash for the specified file, by appending the file's
     * base name to the hashed full path.
     * This allows to hide the underlying file system structure.
     */ 
    public String hashFile(File mainFile) throws IOException, NoSuchAlgorithmException {
        String canonicalPath = mainFile.getCanonicalPath();
        String mainFilePath = FilenameUtils.getPath(canonicalPath);
        
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        md.update(mainFilePath.getBytes());
        return convertToHex(md.digest()) + "-" + mainFile.getName();
    }

    private static String convertToHex(byte[] data) {
        StringBuilder buf = new StringBuilder();
        for (byte b : data) {
            int halfbyte = (b >>> 4) & 0x0F;
            int two_halfs = 0;
            do {
                buf.append((0 <= halfbyte) && (halfbyte <= 9) ? (char) ('0' + halfbyte)
                        : (char) ('a' + (halfbyte - 10)));
                halfbyte = b & 0x0F;
            } while (two_halfs++ < 1);
        }
        return buf.toString();
    }

}

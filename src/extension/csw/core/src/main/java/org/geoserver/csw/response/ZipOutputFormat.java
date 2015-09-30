package org.geoserver.csw.response;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.zip.ZipOutputStream;

import org.apache.commons.io.FileUtils;
import org.geoserver.catalog.FeatureTypeInfo;
import org.geoserver.data.util.IOUtils;
import org.geoserver.ows.Response;
import org.geoserver.platform.Operation;
import org.geoserver.platform.ServiceException;
import org.geotools.data.wfs.internal.GetFeatureRequest;
import org.geotools.util.logging.Logging;

public class ZipOutputFormat extends Response {

    private static final Logger LOGGER = Logging.getLogger(ZipOutputFormat.class);
    
    public ZipOutputFormat() {
        super(List.class);
        // TODO Auto-generated constructor stub
    }

    @Override
    public String getMimeType(Object value, Operation operation) throws ServiceException {
        return "application/zip";
    }

    @Override
    public void write(Object value, OutputStream output, Operation operation) throws IOException,
            ServiceException {

        File tempDir = IOUtils.createTempDirectory("ziptemp");

        // target charset

        try {
            // if an empty result out of feature type with unknown geometry is created, the
            // zip file will be empty and the zip output stream will break
            boolean shapefileCreated = false;
            List<File> files = (List<File>) value;
            for (File file: files) {
                FileUtils.copyFile(file, new File(tempDir, file.getName()));
            }
            ZipOutputStream zipOut = new ZipOutputStream(output);
            IOUtils.zipDirectory(tempDir, zipOut, null);
            zipOut.finish();

            // This is an error, because this closes the output stream too... it's
            // not the right place to do so
            // zipOut.close();
        } finally {
            // make sure we remove the temp directory and its contents completely now
            try {
                FileUtils.deleteDirectory(tempDir);
            } catch (IOException e) {
                LOGGER.warning("Could not delete temp directory: " + tempDir.getAbsolutePath()
                        + " due to: " + e.getMessage());
            }
        }
    }
    
    
//    /**
//     * Get this output format's file name for for the zipped shapefile.
//     * <p>
//     * The output file name is determined as follows:
//     * <ul>
//     * <li>If the {@code GetFeature} request indicated a desired file name, then that one is used as
//     * is. The request may have specified the output file name through the {@code FILENAME } format
//     * option. For example: {@code &format_options=FILENAME:roads.zip}
//     * <li>Otherwise a file name is inferred from the requested feature type(s) name.
//     * </ul>
//     * 
//     * @return the the file name for the zipped shapefile(s)
//     * 
//     */
//    @Override
//    public String getAttachmentFileName(Object value, Operation operation) {
//        File file = ((List<File>) value).get(0);
//        FeatureTypeInfo ftInfo = getFeatureTypeInfo(fc);
//        
//        String filename = null;
//        GetFeatureRequest request = GetFeatureRequest.adapt(operation.getParameters()[0]);
//        if (request != null) {
//            Map<String, ?> formatOptions = request.getFormatOptions();
//            filename = (String) formatOptions.get("FILENAME");
//        }
//        if (filename == null) {
//            filename = new FileNameSource(getClass()).getZipName(ftInfo);
//        }
//        if (filename == null) {
//            filename = ftInfo.getName();
//        }
//        return filename + (filename.endsWith(".zip") ? "" : ".zip");
//    }

}

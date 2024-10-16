/* Copyright (c) 2012 TOPP - www.openplans.org. All rights reserved.
 * This code is licensed under the GPL 2.0 license, availible at the root
 * application directory.
 */
package org.geoserver.wcs2_0.exception;

import java.io.ByteArrayOutputStream;
import static org.geoserver.ows.util.ResponseUtils.baseURL;
import static org.geoserver.ows.util.ResponseUtils.buildSchemaURL;

import java.io.IOException;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;

import javax.servlet.http.HttpServletResponse;
import net.opengis.ows20.ExceptionType;

import net.opengis.ows20.ExceptionReportType;
import net.opengis.ows20.Ows20Factory;
import org.geoserver.ows.Request;
import org.geoserver.ows.ServiceExceptionHandler;
import org.geoserver.ows.util.OwsUtils;
import org.geoserver.platform.OWS20Exception;

import org.geoserver.platform.Service;
import org.geoserver.platform.ServiceException;
import org.geotools.ows.v2_0.OWS;
import org.geotools.ows.v2_0.OWSConfiguration;
import org.geotools.xml.Encoder;


/**
 * A default implementation of {@link ServiceExceptionHandler} which outputs
 * as service exception in a <code>ows:ExceptionReport</code> document.
 * <p>
 * This service exception handler will generate an OWS exception report,
 * see {@linkplain http://schemas.opengis.net/ows/1.1.0/owsExceptionReport.xsd}.
 * </p>
 *
 * @author Justin Deoliveira, The Open Planning Project
 *
 */
public class OWS20ServiceExceptionHandler extends ServiceExceptionHandler {
    /**
     * verbose exception flag controlling whether the exception stack trace will be included in the
     * encoded ows exception report
     */
    protected boolean verboseExceptions = false;

    /**
     * flag that controls what version to use in the ows exception report.
     */
    protected boolean useServiceVersion = false;

    /**
     * Constructor to be called if the exception is not for a particular service.
     *
     */
    public OWS20ServiceExceptionHandler() {
        super(Collections.EMPTY_LIST);
    }

    /**
     * Constructor to be called if the exception is for a particular service.
     *
     * @param services List of services this handler handles exceptions for.
     */
    public OWS20ServiceExceptionHandler(List services) {
        super(services);
    }
    
    /**
     * Constructor to be called if the exception is for a particular service.
     *
     * @param services List of services this handler handles exceptions for.
     */
    public OWS20ServiceExceptionHandler(Service service) {
        super(Arrays.asList(service));
    }

    /**
     * Writes out an OWS ExceptionReport document.
     */
    public void handleServiceException(ServiceException exception, Request request) {
        String version = null;
        if (useServiceVersion && request.getServiceDescriptor() != null) {
            version = request.getServiceDescriptor().getVersion().toString();
        }

        ExceptionReportType report = exceptionReport( exception, verboseExceptions, version );
        
        HttpServletResponse response = request.getHttpResponse();
        if (!request.isSOAP()) {
            //there will already be a SOAP mime type
            response.setContentType("application/xml");
        }

        OWS20Exception ows2ex;
        if(exception instanceof OWS20Exception) {
            ows2ex = (OWS20Exception)exception;
        } else if ( exception.getCause() != null && exception.getCause() instanceof OWS20Exception) {
            ows2ex = (OWS20Exception)exception.getCause();
        } else {
            ows2ex = null;
        }

        //response.setCharacterEncoding( "UTF-8" );
        OWSConfiguration configuration = new OWSConfiguration();

        Encoder encoder = new Encoder(configuration, configuration.schema());
        encoder.setIndenting(true);
        encoder.setIndentSize(2);
        encoder.setLineWidth(60);
        encoder.setOmitXMLDeclaration(request.isSOAP());
        
        // String schemaLocation = buildSchemaURL(baseURL(request.getHttpRequest()), "ows/2.0/owsAll.xsd");
        String schemaLocation = "http://schemas.opengis.net/ows/2.0/owsExceptionReport.xsd";
        encoder.setSchemaLocation(OWS.NAMESPACE, schemaLocation);

        try {

            if(ows2ex != null) {
                if(ows2ex.getHttpCode() != null) {
                    response.setStatus(ows2ex.getHttpCode());
                }
                encoder.encode(report, OWS.ExceptionReport, response.getOutputStream());
            }

        } catch (Exception ex) {
            //throw new RuntimeException(ex);
            // Hmm, not much we can do here.  I guess log the fact that we couldn't write out the exception and be done with it...
            LOGGER.log(Level.INFO, "Problem writing exception information back to calling client:", ex);
        } finally {
            try {
                response.getOutputStream().flush();
            } catch (IOException ioe) {
            }
        }
    }

    /**
     * Flag that controls what version to use in the ows exception report.
     * <p>
     * Setting to true will cause the service version to be used rather than the ows spec version.
     * </p>
     */
    public void setUseServiceVersion(boolean useServiceVersion) {
        this.useServiceVersion = useServiceVersion;
    }

    public static ExceptionReportType exceptionReport(ServiceException exception,
        boolean verboseExceptions, String version) {

        ExceptionType e = Ows20Factory.eINSTANCE.createExceptionType();

        if (exception.getCode() != null) {
            e.setExceptionCode(exception.getCode());
        } else {
            //set a default
            e.setExceptionCode("NoApplicableCode");
        }

        e.setLocator(exception.getLocator());

        //add the message
        StringBuffer sb = new StringBuffer();
        OwsUtils.dumpExceptionMessages(exception, sb, true);
        sb.append("\n");
        sb.append(exception.getExceptionText()); // check this
//        e.getExceptionText().add(sb.toString());
//        e.getExceptionText().addAll(exception.getExceptionText());

        if(verboseExceptions) {
            //add the entire stack trace
            //exception.
            sb.append("\nDetails:\n");
            ByteArrayOutputStream trace = new ByteArrayOutputStream();
            exception.printStackTrace(new PrintStream(trace));
            sb.append(new String(trace.toByteArray()));
        }

        e.setExceptionText(sb.toString());

        ExceptionReportType report = Ows20Factory.eINSTANCE.createExceptionReportType();

        version = version != null ? version : "2.0.0";
        report.setVersion(version);
        report.getException().add(e);

        return report;
    }
}

/* (c) 2014 - 2016 Open Source Geospatial Foundation - all rights reserved
 * (c) 2001 - 2013 OpenPlans
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.security.decorators;

import static org.easymock.EasyMock.createNiceMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.isA;
import static org.easymock.EasyMock.replay;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;
import java.awt.image.renderable.ParameterBlock;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Vector;
import org.easymock.EasyMock;
import org.eclipse.imagen.ImageLayout;
import org.eclipse.imagen.Interpolation;
import org.eclipse.imagen.RenderedOp;
import org.geoserver.catalog.CoverageInfo;
import org.geoserver.catalog.CoverageView;
import org.geoserver.catalog.CoverageViewReader;
import org.geoserver.catalog.Predicates;
import org.geoserver.ows.Dispatcher;
import org.geoserver.ows.Request;
import org.geoserver.platform.Operation;
import org.geoserver.platform.Service;
import org.geoserver.security.CatalogMode;
import org.geoserver.security.CoverageAccessLimits;
import org.geoserver.security.WrapperPolicy;
import org.geoserver.security.impl.SecureObjectsTest;
import org.geotools.api.coverage.grid.Format;
import org.geotools.api.filter.Filter;
import org.geotools.api.parameter.GeneralParameterValue;
import org.geotools.api.parameter.ParameterValue;
import org.geotools.coverage.grid.GeneralGridEnvelope;
import org.geotools.coverage.grid.GridCoverage2D;
import org.geotools.coverage.grid.GridCoverageFactory;
import org.geotools.coverage.grid.io.GridCoverage2DReader;
import org.geotools.coverage.grid.io.StructuredGridCoverage2DReader;
import org.geotools.filter.text.ecql.ECQL;
import org.geotools.gce.imagemosaic.ImageMosaicFormat;
import org.geotools.geometry.GeneralBounds;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.referencing.crs.DefaultGeographicCRS;
import org.geotools.util.Version;
import org.junit.After;
import org.junit.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Polygon;

public class SecuredGridCoverage2DReaderTest extends SecureObjectsTest {

    @After
    public void cleanupRequest() {
        Dispatcher.REQUEST.remove();
    }

    @Test
    public void testFilter() throws Exception {
        final Filter securityFilter = ECQL.toFilter("A > 10");
        final Filter requestFilter = ECQL.toFilter("B < 10");

        // create the mocks we need
        Format format = setupFormat();
        GridCoverage2DReader reader = createNiceMock(GridCoverage2DReader.class);
        expect(reader.getFormat()).andReturn(format).anyTimes();

        setupReadAssertion(reader, requestFilter, securityFilter);

        CoverageAccessLimits accessLimits = new CoverageAccessLimits(CatalogMode.HIDE, securityFilter, null, null);
        SecuredGridCoverage2DReader secured =
                new SecuredGridCoverage2DReader(reader, WrapperPolicy.readOnlyHide(accessLimits));

        final ParameterValue pv = ImageMosaicFormat.FILTER.createValue();
        pv.setValue(requestFilter);
        secured.read(pv);
    }

    @Test
    public void testFilterOnStructured() throws Exception {
        final Filter securityFilter = ECQL.toFilter("A > 10");
        final Filter requestFilter = ECQL.toFilter("B < 10");
        DefaultSecureDataFactory factory = new DefaultSecureDataFactory();

        // create the mocks we need
        Format format = setupFormat();
        StructuredGridCoverage2DReader reader = createNiceMock(StructuredGridCoverage2DReader.class);
        expect(reader.getFormat()).andReturn(format).anyTimes();

        setupReadAssertion(reader, requestFilter, securityFilter);

        CoverageAccessLimits accessLimits = new CoverageAccessLimits(CatalogMode.HIDE, securityFilter, null, null);
        Object securedObject = factory.secure(reader, WrapperPolicy.readOnlyHide(accessLimits));
        assertTrue(securedObject instanceof SecuredStructuredGridCoverage2DReader);
        SecuredStructuredGridCoverage2DReader secured = (SecuredStructuredGridCoverage2DReader) securedObject;

        final ParameterValue pv = ImageMosaicFormat.FILTER.createValue();
        pv.setValue(requestFilter);
        secured.read(pv);
    }

    @Test
    public void testCoverageViewSecured() throws Exception {
        DefaultSecureDataFactory factory = new DefaultSecureDataFactory();

        GridCoverage2DReader reader = createNiceMock(GridCoverage2DReader.class);
        expect(reader.getOriginalEnvelope())
                .andReturn(new GeneralBounds(new double[] {-90, -90}, new double[] {90, 90}))
                .anyTimes();
        expect(reader.getOriginalGridRange())
                .andReturn(new GeneralGridEnvelope(new Rectangle(0, 0, 100, 100)))
                .anyTimes();
        expect(reader.getCoordinateReferenceSystem())
                .andReturn(DefaultGeographicCRS.WGS84)
                .anyTimes();
        BufferedImage bi = new BufferedImage(100, 100, BufferedImage.TYPE_BYTE_GRAY);
        expect(reader.getImageLayout("coverageView"))
                .andReturn(new ImageLayout(bi))
                .anyTimes();
        replay(reader);

        CoverageView coverageView = createNiceMock(CoverageView.class);
        CoverageInfo coverageInfo = createNiceMock(CoverageInfo.class);
        expect(coverageView.getName()).andReturn("coverageView").anyTimes();
        CoverageView.CoverageBand coverageBand = new CoverageView.CoverageBand(
                Collections.singletonList(new CoverageView.InputCoverageBand("coverageView", "band1")),
                "band1",
                0,
                CoverageView.CompositionType.BAND_SELECT);
        List<CoverageView.CoverageBand> bands = Collections.singletonList(coverageBand);
        expect(coverageView.getCoverageBands()).andReturn(bands).anyTimes();
        expect(coverageView.getBand(0)).andReturn(coverageBand).anyTimes();
        expect(coverageView.getSelectedResolution())
                .andReturn(CoverageView.SelectedResolution.BEST)
                .anyTimes();
        expect(coverageView.getEnvelopeCompositionType())
                .andReturn(CoverageView.EnvelopeCompositionType.UNION)
                .anyTimes();
        replay(coverageView);

        CoverageViewReader viewReader = new CoverageViewReader(reader, coverageView, coverageInfo, null);
        CoverageAccessLimits accessLimits = new CoverageAccessLimits(CatalogMode.HIDE, null, null, null);

        Object securedObject = factory.secure(viewReader, WrapperPolicy.readOnlyHide(accessLimits));
        assertTrue(securedObject instanceof SecuredGridCoverage2DReader);

        securedObject = factory.secure(viewReader.getFormat(), WrapperPolicy.readOnlyHide(accessLimits));
        assertTrue(securedObject instanceof SecuredGridFormat);
    }

    @Test
    public void testRasterFilterScalesToRequestedMapRasterArea() throws Exception {
        GridCoverage2D source = createCoverage(100, 100);
        GridCoverage2DReader reader = createNiceMock(GridCoverage2DReader.class);
        expect(reader.read(isA(GeneralParameterValue[].class))).andReturn(source);
        EasyMock.replay(reader);

        setRequestSize(200, 80);
        CoverageAccessLimits accessLimits =
                new CoverageAccessLimits(CatalogMode.HIDE, null, createRasterFilter(), null);
        SecuredGridCoverage2DReader secured =
                new SecuredGridCoverage2DReader(reader, WrapperPolicy.readOnlyHide(accessLimits));

        GridCoverage2D result = secured.read(new GeneralParameterValue[] {interpolationParam()});

        assertNotNull(result);
        RenderedImage image = result.getRenderedImage();
        assertEquals(200, image.getWidth());
        assertEquals(80, image.getHeight());
        assertEquals(25d, result.getEnvelope2D().getMinX(), 0d);
        assertEquals(75d, result.getEnvelope2D().getMaxX(), 0d);

        assertEquals(
                Interpolation.getInstance(Interpolation.INTERP_BILINEAR),
                getScalingInterpolation(result.getRenderedImage()));
    }

    @Test
    public void testRasterFilterOutsideCoverageReturnsNull() throws Exception {
        GridCoverage2D source = createCoverage(100, 100);
        GridCoverage2DReader reader = createNiceMock(GridCoverage2DReader.class);
        expect(reader.read(isA(GeneralParameterValue[].class))).andReturn(source);
        EasyMock.replay(reader);

        MultiPolygon rasterFilter = createRasterFilter(200, 250, 200, 250);
        CoverageAccessLimits accessLimits = new CoverageAccessLimits(CatalogMode.HIDE, null, rasterFilter, null);
        SecuredGridCoverage2DReader secured =
                new SecuredGridCoverage2DReader(reader, WrapperPolicy.readOnlyHide(accessLimits));

        assertNull(secured.read(new GeneralParameterValue[0]));
    }

    private static void setupReadAssertion(
            GridCoverage2DReader reader, final Filter requestFilter, final Filter securityFilter) throws IOException {
        // the assertion
        expect(reader.read(isA(GeneralParameterValue[].class))).andAnswer(() -> {
            GeneralParameterValue[] params = (GeneralParameterValue[]) EasyMock.getCurrentArguments()[0];
            ParameterValue param = (ParameterValue) params[0];
            Filter filter = (Filter) param.getValue();
            assertEquals(Predicates.and(requestFilter, securityFilter), filter);
            return null;
        });
        EasyMock.replay(reader);
    }

    private Format setupFormat() {
        Format format = createNiceMock(Format.class);
        expect(format.getReadParameters())
                .andReturn(new ImageMosaicFormat().getReadParameters())
                .anyTimes();
        EasyMock.replay(format);
        return format;
    }

    private static GridCoverage2D createCoverage(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
        ReferencedEnvelope envelope = new ReferencedEnvelope(0, 100, 0, 100, DefaultGeographicCRS.WGS84);
        return new GridCoverageFactory().create("test", image, envelope);
    }

    private static MultiPolygon createRasterFilter() {
        return createRasterFilter(25, 75, 25, 75);
    }

    private static MultiPolygon createRasterFilter(double minX, double maxX, double minY, double maxY) {
        GeometryFactory geometryFactory = new GeometryFactory();
        Polygon polygon = geometryFactory.createPolygon(new Coordinate[] {
            new Coordinate(minX, minY),
            new Coordinate(maxX, minY),
            new Coordinate(maxX, maxY),
            new Coordinate(minX, maxY),
            new Coordinate(minX, minY)
        });
        return geometryFactory.createMultiPolygon(new Polygon[] {polygon});
    }

    @SuppressWarnings("unchecked")
    private static ParameterValue<Interpolation> interpolationParam() {
        ParameterValue<Interpolation> interpolation =
                (ParameterValue<Interpolation>) ImageMosaicFormat.INTERPOLATION.createValue();
        interpolation.setValue(Interpolation.getInstance(Interpolation.INTERP_BILINEAR));
        return interpolation;
    }

    private static void setRequestSize(int width, int height) {
        Request request = new Request();
        Service service = new Service("WMS", new Object(), new Version("1.3.0"), Collections.singletonList("GetMap"));
        request.setOperation(new Operation("GetMap", service, null, new Object[] {new MapSize(width, height)}));
        Dispatcher.REQUEST.set(request);
    }

    private static Interpolation getScalingInterpolation(RenderedImage image) {
        RenderedOp scalingOp = getScalingOpImage(image);
        if (scalingOp == null) {
            return null;
        }

        ParameterBlock parameterBlock = scalingOp.getParameterBlock();
        for (int i = 0; i < parameterBlock.getNumParameters(); i++) {
            Object param = parameterBlock.getObjectParameter(i);
            if (param instanceof Interpolation) {
                return (Interpolation) param;
            }
        }
        return null;
    }

    private static RenderedOp getScalingOpImage(RenderedImage image) {
        Vector<RenderedImage> sources = image.getSources();
        if (sources == null) {
            return null;
        }

        for (RenderedImage source : sources) {
            if (source instanceof RenderedOp) {
                RenderedOp op = (RenderedOp) source;
                if ("Scale".equalsIgnoreCase(op.getOperationName())) {
                    return op;
                }
            }
            RenderedOp op = getScalingOpImage(source);
            if (op != null) {
                return op;
            }
        }
        return null;
    }

    public static class MapSize {
        private final int width;
        private final int height;

        public MapSize(int width, int height) {
            this.width = width;
            this.height = height;
        }

        public int getWidth() {
            return width;
        }

        public int getHeight() {
            return height;
        }
    }
}

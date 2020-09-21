/* (c) 2014 - 2020 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.catalog;

import it.geosolutions.imageio.plugins.cog.CogImageReadParam;
import it.geosolutions.imageioimpl.plugins.cog.*;
import org.geotools.coverage.grid.io.imageio.COGReaderInputObject;
import org.geotools.coverage.grid.io.imageio.ReaderInputObject;
import org.geotools.util.factory.Hints;
import org.opengis.coverage.grid.GridCoverageReader;

import javax.annotation.Nullable;
import javax.imageio.spi.ImageInputStreamSpi;
import javax.imageio.spi.ImageReaderSpi;
import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;

/**
 * Attempts to convert the source input object for a {@link GridCoverageReader} to {@link File}.
 *
 * @author joshfix Created on 2/25/20
 */
public class CoverageReaderInputObjectCOGConverter implements CoverageReaderInputObjectConverter<ReaderInputObject> {

    private final static String COG_PREFIX = "cog://";
    private static final ImageInputStreamSpi COG_IMAGE_INPUT_STREAM_SPI = new CogImageInputStreamSpi();

    private static final ImageReaderSpi COG_IMAGE_READER_SPI = new CogImageReaderSpi();

    private final Catalog catalog;

    public CoverageReaderInputObjectCOGConverter(Catalog catalog) {
        this.catalog = catalog;
    }

    /**
     * Performs the conversion of the input object to a file object. If this converter is not able
     * to convert the input to a File, an empty {@link Optional} will be returned.
     *
     * @param input The input object.
     * @param coverageInfo The grid coverage metadata, may be <code>null</code>.
     * @param hints Hints to use when loading the coverage, may be <code>null</code>.
     * @return
     */
    @Override
    public Optional<ReaderInputObject> convert(
            Object input, @Nullable CoverageInfo coverageInfo, @Nullable Hints hints) {
        if (!(input instanceof String)) {
            return Optional.empty();
        }
        String urlString = (String) input;
        return canConvert(urlString) ? convertReaderInputObject(urlString) : Optional.empty();
    }

    /**
     * Checks to see if the input string is a file URI.
     *
     * @param input The input string.
     * @return Value representing whether or not this converter is able to convert the provided
     *     input to File.
     */
    protected boolean canConvert(String input) {
        return input.startsWith(COG_PREFIX);
    }

    /**
     * Performs the conversion to File.
     *
     * @param input The input string.
     * @return The Optional object containing the File
     */
    protected Optional<ReaderInputObject> convertReaderInputObject(String input) {

        String realUrl = input.substring(COG_PREFIX.length());
        URI uri = null;
        try {
            uri = new URI(realUrl);
        } catch (URISyntaxException e) {
            return Optional.empty();
        }
        HttpRangeReader rangeReader = new HttpRangeReader(uri, CogImageReadParam.DEFAULT_HEADER_LENGTH);
        ReaderInputObject object = new COGReaderInputObject(COG_IMAGE_READER_SPI, COG_IMAGE_INPUT_STREAM_SPI, uri,
                rangeReader);
        return Optional.of(object);
    }
}

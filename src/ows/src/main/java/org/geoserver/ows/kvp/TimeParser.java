/* (c) 2014 - 2016 Open Source Geospatial Foundation - all rights reserved
 * (c) 2001 - 2013 OpenPlans
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.ows.kvp;

import java.util.Set;
import java.util.logging.Logger;
import org.geoserver.platform.ServiceException;
import org.geotools.util.logging.Logging;

/**
 * Parses the {@code time} parameter of the request. The date, time and period are expected to be
 * formatted according ISO-8601 standard.
 *
 * @author Cedric Briancon
 * @author Martin Desruisseaux
 * @author Simone Giannecchini, GeoSolutions SAS
 * @author Jonathan Meyer, Applied Information Sciences, jon@gisjedi.com
 * @version $Id$
 */
public class TimeParser extends org.geotools.util.TimeParser{
    static final Logger LOGGER = Logging.getLogger(TimeParser.class);

    private static final int DEFAULT_MAX_ELEMENTS_TIMES_KVP = 100;

    /** Builds a default TimeParser with no provided maximum number of times */
    public TimeParser() {
        super(DEFAULT_MAX_ELEMENTS_TIMES_KVP);
    }

    @Override
    public void checkMaxTimes(Set result, int maxValues) {
        // limiting number of elements we can create
        if (maxValues > 0 && result.size() > maxValues) {
            throw new ServiceException(
                    "More than " + maxValues + " times specified in the request, bailing out.",
                    ServiceException.INVALID_PARAMETER_VALUE,
                    "time");
        }
    }

}

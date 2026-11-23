/* (c) 2026 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.pgstac;

import java.io.IOException;

/** A PgSTAC call failure already mapped to the HTTP status code the caller should return. */
public class PgStacException extends IOException {

    private final int httpStatus;

    public PgStacException(int httpStatus, String message) {
        super(message);
        this.httpStatus = httpStatus;
    }

    public PgStacException(int httpStatus, String message, Throwable cause) {
        super(message, cause);
        this.httpStatus = httpStatus;
    }

    public int getHttpStatus() {
        return httpStatus;
    }
}

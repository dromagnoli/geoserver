/* (c) 2026 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.pgstac;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.sql.SQLException;
import org.junit.Test;

public class DefaultPgStacFacadeTest {

    @Test
    public void testIsSupportedVersion() {
        assertTrue(DefaultPgStacFacade.isSupportedVersion("0.10.0"));
        assertTrue(DefaultPgStacFacade.isSupportedVersion("0.10.1"));
        assertFalse(DefaultPgStacFacade.isSupportedVersion("0.9.11"));
        assertFalse(DefaultPgStacFacade.isSupportedVersion("0.11.0"));
        assertFalse(DefaultPgStacFacade.isSupportedVersion("1.0.0"));
        assertFalse(DefaultPgStacFacade.isSupportedVersion("0"));
        assertFalse(DefaultPgStacFacade.isSupportedVersion(""));
        assertFalse(DefaultPgStacFacade.isSupportedVersion("foo"));
        assertFalse(DefaultPgStacFacade.isSupportedVersion(null));
    }

    @Test
    public void testTranslateUniqueViolation() {
        assertStatus(409, "duplicate key", "23505");
    }

    @Test
    public void testTranslateForeignKeyViolation() {
        assertStatus(400, "violates foreign key constraint", "23503");
    }

    @Test
    public void testTranslateRaisedValidationErrors() {
        assertStatus(400, "Operator % Not Supported.", "P0001");
        assertStatus(400, "Term foo is not found in queryables.", "P0001");
        // messages added in PgSTAC 0.10, no longer matching the 0.9 wording
        assertStatus(400, "Temporal operator t_foo is not supported.", "P0001");
        assertStatus(400, "The in operator takes a value and an array of values.", "P0001");
        assertStatus(400, "Invalid limit -1: must be a non-negative integer", "P0001");
    }

    @Test
    public void testTranslateDataExceptions() {
        assertStatus(400, "Queryable foo is global; use delete_queryable", "22023");
        assertStatus(400, "integer out of range", "22003");
    }

    @Test
    public void testTranslateNoDataFound() {
        assertStatus(404, "Collection foo does not exist", "P0002");
    }

    @Test
    public void testTranslateFallsBackTo500() {
        assertStatus(500, "connection reset", "08006");
        assertStatus(500, null, "58000");
        assertStatus(500, "no state", null);
    }

    private static void assertStatus(int expected, String message, String sqlState) {
        assertEquals(
                expected,
                DefaultPgStacFacade.translate(new SQLException(message, sqlState))
                        .getHttpStatus());
    }
}

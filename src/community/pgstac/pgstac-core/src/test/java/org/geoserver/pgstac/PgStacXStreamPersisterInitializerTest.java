/* (c) 2026 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.pgstac;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.geoserver.config.util.XStreamPersister;
import org.geoserver.config.util.XStreamPersisterFactory;
import org.geoserver.platform.GeoServerExtensionsHelper;
import org.geoserver.security.GeoServerSecurityManager;
import org.geoserver.security.password.ConfigurationPasswordEncryptionHelper;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/** Verifies the {@code passwd} entry in {@link PgStacServiceInfo#getConnectionParameters()} round-trips encrypted. */
public class PgStacXStreamPersisterInitializerTest {

    private static final String PLAIN_PASSWORD = "s3cr3t";
    private static final String ENCODED_PASSWORD = "OPAQUE-TOKEN-4711";

    private XStreamPersister persister;

    @Before
    public void setUp() {
        ConfigurationPasswordEncryptionHelper encryptionHelper = mock(ConfigurationPasswordEncryptionHelper.class);
        when(encryptionHelper.encode(PLAIN_PASSWORD)).thenReturn(ENCODED_PASSWORD);
        when(encryptionHelper.decode(ENCODED_PASSWORD)).thenReturn(PLAIN_PASSWORD);

        GeoServerSecurityManager securityManager = mock(GeoServerSecurityManager.class);
        when(securityManager.getConfigPasswordEncryptionHelper()).thenReturn(encryptionHelper);
        GeoServerExtensionsHelper.singleton("secMgr", securityManager, GeoServerSecurityManager.class);

        persister = new XStreamPersisterFactory().createXMLPersister();
        new PgStacXStreamPersisterInitializer().init(persister);
    }

    @After
    public void tearDown() {
        GeoServerExtensionsHelper.clear();
    }

    @Test
    public void testPasswordEncryptedOnSaveAndDecryptedOnLoad() throws Exception {
        PgStacServiceInfoImpl info = new PgStacServiceInfoImpl();
        Map<String, Serializable> params = new LinkedHashMap<>();
        params.put("host", "localhost");
        params.put(PgStacXStreamPersisterInitializer.PASSWORD_KEY, PLAIN_PASSWORD);
        info.setConnectionParameters(params);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        persister.save(info, out);

        String xml = out.toString(StandardCharsets.UTF_8);
        assertFalse("plaintext password must not appear in persisted XML", xml.contains(PLAIN_PASSWORD));

        PgStacServiceInfoImpl reloaded =
                persister.load(new ByteArrayInputStream(out.toByteArray()), PgStacServiceInfoImpl.class);

        assertEquals("localhost", reloaded.getConnectionParameters().get("host"));
        assertEquals(
                PLAIN_PASSWORD, reloaded.getConnectionParameters().get(PgStacXStreamPersisterInitializer.PASSWORD_KEY));
    }
}

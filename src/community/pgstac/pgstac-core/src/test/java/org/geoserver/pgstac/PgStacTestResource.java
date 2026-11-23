/* (c) 2026 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.pgstac;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import org.geotools.data.postgis.PostgisNGDataStoreFactory;
import org.junit.Assume;
import org.junit.rules.ExternalResource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/** A PostgreSQL + PostGIS container with an empty PgSTAC 0.10.1 schema, skipping the tests when Docker is missing. */
public class PgStacTestResource extends ExternalResource {

    // postgis/postgis:18-3.6-alpine pinned by digest: PostgreSQL 18.6, PostGIS 3.6.4, GEOS 3.14.1, as run by DLR
    private static final String IMAGE =
            "postgis/postgis@sha256:c53a483fc274072dfa02cd491efa79f3c8ae0a10774a0ece6c9c53b6a632cdef";

    // src/pgstac/pgstac.sql of PgSTAC v0.10.1 (MIT license), unmodified,
    // sha256 d2ab8b3647b82895b06abd6b83e64f6e8b31bd76618237c82ecb07186c8a8cb2
    private static final String PGSTAC_SQL = "org/geoserver/pgstac/pgstac-0.10.1.sql";

    private PostgreSQLContainer container;

    @Override
    protected void before() {
        Assume.assumeTrue(
                "Docker is required for PgSTAC tests",
                DockerClientFactory.instance().isDockerAvailable());
        DockerImageName image = DockerImageName.parse(IMAGE).asCompatibleSubstituteFor("postgres");
        // scripts in docker-entrypoint-initdb.d run once on the empty database, before the container reports ready;
        // the long password is needed by SCRAM under a FIPS provider, see PostGISTestResource
        container = new PostgreSQLContainer(image)
                .withPassword("geoserver-pgstac-test")
                .withCopyFileToContainer(
                        MountableFile.forClasspathResource(PGSTAC_SQL), "/docker-entrypoint-initdb.d/999_pgstac.sql");
        container.start();
    }

    @Override
    protected void after() {
        if (container != null) {
            container.stop();
        }
    }

    /** PostGIS store parameters for the container, usable as {@link PgStacServiceInfo} connection parameters. */
    public Map<String, Serializable> getConnectionParameters() {
        Map<String, Serializable> params = new HashMap<>();
        params.put(PostgisNGDataStoreFactory.DBTYPE.key, "postgis");
        params.put(PostgisNGDataStoreFactory.HOST.key, container.getHost());
        params.put(PostgisNGDataStoreFactory.PORT.key, container.getMappedPort(5432));
        params.put(PostgisNGDataStoreFactory.DATABASE.key, container.getDatabaseName());
        params.put(PostgisNGDataStoreFactory.USER.key, container.getUsername());
        params.put(PostgisNGDataStoreFactory.PASSWD.key, container.getPassword());
        return params;
    }
}

/* (c) 2025 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.eumetsat.pinning.config;

import java.io.IOException;
import java.io.Serializable;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.PostConstruct;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;
import org.geoserver.catalog.Catalog;
import org.geoserver.config.GeoServerDataDirectory;
import org.geoserver.eumetsat.pinning.auth.AuthClientProperties;
import org.geoserver.platform.GeoServerResourceLoader;
import org.geoserver.platform.resource.Resource;
import org.geoserver.security.PropertyFileWatcher;
import org.geotools.util.logging.Logging;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;

@Component
@DependsOn("catalog")
public class PinningServiceConfig implements Serializable, Cloneable {

    private static final Logger LOGGER = Logging.getLogger(PinningServiceConfig.class);

    private static final String CONFIG_FILENAME = "pinning/service.properties";

    private final Catalog catalog;
    private PropertyFileWatcher propertyFileWatcher;
    private Properties properties;

    public PinningServiceConfig(Catalog catalog) {
        this.catalog = catalog;
    }

    @PostConstruct
    private void initConfig() throws IOException {
        GeoServerResourceLoader loader = catalog.getResourceLoader();
        GeoServerDataDirectory directory = new GeoServerDataDirectory(loader);
        Resource resource = directory.get(CONFIG_FILENAME);
        Resource.Type resourceType = resource.getType();
        if (resourceType.equals(Resource.Type.UNDEFINED)) {
            LOGGER.log(
                    Level.SEVERE,
                    "Pinning service configuration not found at "
                            + resource.path()
                            + "\nThe pinning service operations will potentially fail");
            return;
        }
        LOGGER.config("Loading Pinning Service Config from: " + resource.path());
        this.propertyFileWatcher = new PropertyFileWatcher(resource);
        this.properties = propertyFileWatcher.getProperties();
    }

    @Bean
    public PropertyFileWatcher propertyFileWatcher() {
        return propertyFileWatcher;
    }

    @Bean
    public Properties properties() {
        return properties;
    }

    public String preferencesUrl() {
        return properties.getProperty("preferences.url", "");
    }

    public Integer batchSize() {
        return Integer.parseInt(properties.getProperty("batch.size", "100"));
    }

    public Integer pinningMinutes() {
        return Integer.parseInt(properties.getProperty("pinning.minutes", "60"));
    }

    public Integer preferencesPagesSize() {
        return Integer.parseInt(properties.getProperty("preferences.pages.size", "5"));
    }

    public AuthClientProperties wso2AuthClientProperties() {
        return AuthClientProperties.builder()
                .tokenUri(properties.getProperty("wso2.token.uri"))
                .clientId(properties.getProperty("wso2.consumer.key"))
                .clientSecret(properties.getProperty("wso2.consumer.secret"))
                .build();
    }

    public AuthClientProperties oAuth2AuthClientProperties() {
        return AuthClientProperties.builder()
                .tokenUri(properties.getProperty("oauth2.token.uri"))
                .clientId(properties.getProperty("oauth2.client.id"))
                .clientSecret(properties.getProperty("oauth2.client.secret"))
                .username(properties.getProperty("oauth2.username"))
                .password(properties.getProperty("oauth2.password"))
                .build();
    }

    public String jndiDatasourceName() {
        return properties.getProperty("jndi.datasource.name", "");
    }

    @Bean
    public DataSource dataSource() throws NamingException {
        Context ctx = new InitialContext();
        // Search for the eumetsat datasource configured using JNDI
        return (DataSource) ctx.lookup("java:/comp/env/jdbc/" + jndiDatasourceName());
    }
}

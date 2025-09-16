package org.geoserver.eumetsat.pinning.config;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.geoserver.eumetsat.pinning.auth.AuthClientProperties;
import org.geoserver.eumetsat.pinning.auth.OAuth2RestTemplateInterceptor;
import org.geoserver.eumetsat.pinning.auth.Wso2RestTemplateInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.InMemoryOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.OAuth2AuthorizationContext;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProvider;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

/**
 * Adds to the Spring context the beans necessary to handle an authentication flow prior to
 * executing HTTP requests through the defined {@link RestTemplate}.
 *
 * <p>If the Spring profile {@code wso2} is active at startup, the {@link RestTemplate} will use
 * WSO2 {@code client_credentials} authentication. If no profile is specified, the authentication
 * will rely on the OAuth2 password grant flow.
 */
@Configuration
public class AuthConfig {

    private static final String EUMETSAT_OAUTH2_REGISTRATION_ID = "eumetsat_oauth2";
    private static final String EUMETSAT_WSO2_REGISTRATION_ID = "eumetsat_wso2";
    private static final String PRINCIPAL_NAME = "geoserver-pinning-service";

    @Bean
    @Profile("!wso2")
    public ClientRegistration oAuth2ClientRegistration(PinningServiceConfig pinningServiceConfig) {
        AuthClientProperties authClientProperties =
                pinningServiceConfig.oAuth2AuthClientProperties();
        return ClientRegistration.withRegistrationId(EUMETSAT_OAUTH2_REGISTRATION_ID)
                .tokenUri(authClientProperties.tokenUri())
                .clientId(authClientProperties.clientId())
                .clientSecret(authClientProperties.clientSecret())
                .authorizationGrantType(AuthorizationGrantType.PASSWORD)
                .build();
    }

    @Bean
    @Profile("wso2")
    public ClientRegistration wso2ClientRegistration(PinningServiceConfig pinningServiceConfig) {
        AuthClientProperties authClientProperties = pinningServiceConfig.wso2AuthClientProperties();
        return ClientRegistration.withRegistrationId(EUMETSAT_WSO2_REGISTRATION_ID)
                .tokenUri(authClientProperties.tokenUri())
                .clientId(authClientProperties.clientId())
                .clientSecret(authClientProperties.clientSecret())
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .build();
    }

    @Bean
    public ClientRegistrationRepository clientRegistrationRepository(
            ClientRegistration clientRegistration) {
        return new InMemoryClientRegistrationRepository(clientRegistration);
    }

    @Bean
    public OAuth2AuthorizedClientService oAuth2AuthorizedClientService(
            ClientRegistrationRepository clientRegistrationRepository) {
        return new InMemoryOAuth2AuthorizedClientService(clientRegistrationRepository);
    }

    @Bean
    @Profile("!wso2")
    public OAuth2AuthorizedClientManager oAuth2AuthorizedClientManager(
            ClientRegistrationRepository clientRegistrationRepository,
            OAuth2AuthorizedClientService authorizedClientService) {

        AuthorizedClientServiceOAuth2AuthorizedClientManager manager =
                new AuthorizedClientServiceOAuth2AuthorizedClientManager(
                        clientRegistrationRepository, authorizedClientService);

        OAuth2AuthorizedClientProvider provider =
                OAuth2AuthorizedClientProviderBuilder.builder().password().refreshToken().build();

        manager.setAuthorizedClientProvider(provider);

        manager.setContextAttributesMapper(
                authorizeRequest -> {
                    Map<String, Object> requestAttributes = authorizeRequest.getAttributes();
                    String username =
                            (String)
                                    requestAttributes.get(
                                            OAuth2AuthorizationContext.USERNAME_ATTRIBUTE_NAME);
                    String password =
                            (String)
                                    requestAttributes.get(
                                            OAuth2AuthorizationContext.PASSWORD_ATTRIBUTE_NAME);

                    Map<String, Object> contextAttributes = new HashMap<>();
                    if (StringUtils.hasText(username)) {
                        contextAttributes.put(
                                OAuth2AuthorizationContext.USERNAME_ATTRIBUTE_NAME, username);
                    }
                    if (StringUtils.hasText(password)) {
                        contextAttributes.put(
                                OAuth2AuthorizationContext.PASSWORD_ATTRIBUTE_NAME, password);
                    }

                    return contextAttributes;
                });

        return manager;
    }

    @Bean
    @Profile("wso2")
    public OAuth2AuthorizedClientManager wso2AuthorizedClientManager(
            ClientRegistrationRepository clientRegistrationRepository,
            OAuth2AuthorizedClientService authorizedClientService) {

        AuthorizedClientServiceOAuth2AuthorizedClientManager manager =
                new AuthorizedClientServiceOAuth2AuthorizedClientManager(
                        clientRegistrationRepository, authorizedClientService);

        OAuth2AuthorizedClientProvider provider =
                OAuth2AuthorizedClientProviderBuilder.builder().clientCredentials().build();

        manager.setAuthorizedClientProvider(provider);

        return manager;
    }

    @Bean
    @Profile("!wso2")
    public OAuth2RestTemplateInterceptor oAuth2RestTemplateInterceptor(
            OAuth2AuthorizedClientManager oAuth2AuthorizedClientManager,
            PinningServiceConfig pinningServiceConfig) {
        AuthClientProperties authClientProperties =
                pinningServiceConfig.oAuth2AuthClientProperties();
        return new OAuth2RestTemplateInterceptor(
                oAuth2AuthorizedClientManager,
                EUMETSAT_OAUTH2_REGISTRATION_ID,
                PRINCIPAL_NAME,
                authClientProperties.username(),
                authClientProperties.password());
    }

    @Bean
    @Profile("wso2")
    public Wso2RestTemplateInterceptor wso2RestTemplateInterceptor(
            OAuth2AuthorizedClientManager wso2AuthorizedClientManager, Environment env) {
        return new Wso2RestTemplateInterceptor(
                wso2AuthorizedClientManager, EUMETSAT_WSO2_REGISTRATION_ID, PRINCIPAL_NAME);
    }

    @Bean
    public RestTemplate authenticatedRestTemplate(ClientHttpRequestInterceptor requestInterceptor) {
        RestTemplate restTemplate = new RestTemplate();
        restTemplate.setInterceptors(List.of(requestInterceptor));
        return restTemplate;
    }
}

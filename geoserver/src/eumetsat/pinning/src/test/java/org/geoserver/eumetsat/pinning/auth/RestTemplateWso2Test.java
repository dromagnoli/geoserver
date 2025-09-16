package org.geoserver.eumetsat.pinning.auth;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.common.ConsoleNotifier;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import java.util.HashMap;
import java.util.List;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.AuthorizationCodeOAuth2AuthorizedClientProvider;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.InMemoryOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

public class RestTemplateWso2Test {

    private static final String PROTECTED_RESOURCE_URI = "https://api.test/data";

    private static WireMockServer wireMockServer;

    @BeforeClass
    public static void setup() {
        wireMockServer =
                new WireMockServer(
                        WireMockConfiguration.options()
                                .port(8080)
                                .usingFilesUnderDirectory("src/test/resources/wiremock")
                                .notifier(new ConsoleNotifier("wiremock", true)));
        wireMockServer.start();
    }

    @AfterClass
    public static void cleanup() {
        wireMockServer.stop();
    }

    @Test
    public void requestWithToken() {

        Wso2RestTemplateInterceptor wso2RestTemplateInterceptor =
                new Wso2RestTemplateInterceptor(
                        createAuthorizedClientManagerMock(), "test-client", "test-principal");

        RestTemplate restTemplate = new RestTemplate();
        restTemplate.setInterceptors(List.of(wso2RestTemplateInterceptor));

        MockRestServiceServer mockServer = MockRestServiceServer.createServer(restTemplate);

        mockServer
                .expect(requestTo(PROTECTED_RESOURCE_URI))
                .andExpect(header("Authorization", "Bearer mock-token"))
                .andRespond(withSuccess("{\"result\":\"ok\"}", MediaType.APPLICATION_JSON));

        String result = restTemplate.getForObject(PROTECTED_RESOURCE_URI, String.class);

        Assert.assertNotNull(result);
        assertTrue(result.contains("ok"));
        mockServer.verify();
    }

    @Test
    public void requestWithNewToken() {

        wireMockServer.setScenarioState("WSO2", "TOKEN_EXPIRED");
        Wso2RestTemplateInterceptor wso2RestTemplateInterceptor =
                new Wso2RestTemplateInterceptor(
                        createAuthorizedClientManagerMock(), "test-client", "test-principal");

        RestTemplate oauth2RestTemplate = new RestTemplate();
        oauth2RestTemplate.setInterceptors(List.of(wso2RestTemplateInterceptor));

        MockRestServiceServer mockServer = MockRestServiceServer.createServer(oauth2RestTemplate);

        /* first request - received token is about to expire */
        mockServer
                .expect(requestTo(PROTECTED_RESOURCE_URI))
                .andExpect(header("Authorization", "Bearer expired-mock-token"))
                .andRespond(withStatus(HttpStatus.CONTINUE));
        /* second request - new token expected */
        mockServer
                .expect(requestTo(PROTECTED_RESOURCE_URI))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer mock-token"))
                .andRespond(withSuccess("{\"result\":\"ok\"}", MediaType.APPLICATION_JSON));

        /* first request */
        oauth2RestTemplate.delete(PROTECTED_RESOURCE_URI);

        /* reset scenario state to enable valid token retrieval */
        wireMockServer.resetScenario("WSO2");

        /* second request - with new token */
        String result = oauth2RestTemplate.getForObject(PROTECTED_RESOURCE_URI, String.class);

        Assert.assertNotNull(result);
        assertTrue(result.contains("ok"));
        mockServer.verify();
    }

    @Test
    public void requestWithErrorObtainingToken() {

        AuthorizedClientServiceOAuth2AuthorizedClientManager authorizedClientManagerMock =
                createAuthorizedClientManagerMock();

        /* provoking error removing authorized client provider */
        authorizedClientManagerMock.setAuthorizedClientProvider(
                new AuthorizationCodeOAuth2AuthorizedClientProvider());

        Wso2RestTemplateInterceptor wso2RestTemplateInterceptor =
                new Wso2RestTemplateInterceptor(
                        authorizedClientManagerMock, "test-client", "test-principal");

        RestTemplate restTemplate = new RestTemplate();
        restTemplate.setInterceptors(List.of(wso2RestTemplateInterceptor));

        IllegalStateException exception =
                assertThrows(
                        IllegalStateException.class,
                        () -> restTemplate.getForObject(PROTECTED_RESOURCE_URI, String.class));
        assertTrue(exception.getMessage().contains("WSO2 token for test-client"));
    }

    private AuthorizedClientServiceOAuth2AuthorizedClientManager
            createAuthorizedClientManagerMock() {

        ClientRegistration clientRegistration =
                ClientRegistration.withRegistrationId("test-client")
                        .tokenUri(wireMockServer.baseUrl() + "/auth/wso2/token")
                        .clientId("pinning-service")
                        .clientSecret("pinningservicewso2secret")
                        .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                        .scope("read")
                        .build();
        InMemoryClientRegistrationRepository registrationRepository =
                new InMemoryClientRegistrationRepository(clientRegistration);
        InMemoryOAuth2AuthorizedClientService authorizedClientService =
                new InMemoryOAuth2AuthorizedClientService(registrationRepository);

        AuthorizedClientServiceOAuth2AuthorizedClientManager manager =
                new AuthorizedClientServiceOAuth2AuthorizedClientManager(
                        registrationRepository, authorizedClientService);

        manager.setAuthorizedClientProvider(
                OAuth2AuthorizedClientProviderBuilder.builder().clientCredentials().build());

        manager.setContextAttributesMapper(request -> new HashMap<>(request.getAttributes()));

        return manager;
    }
}

package org.geoserver.eumetsat.pinning.auth;

import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.oauth2.client.OAuth2AuthorizationContext;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;

public class OAuth2RestTemplateInterceptor implements ClientHttpRequestInterceptor {

    private final OAuth2AuthorizedClientManager authorizedClientManager;
    private final String clientRegistrationId;
    private final String principalName;
    private final String username;
    private final String password;

    public OAuth2RestTemplateInterceptor(
            OAuth2AuthorizedClientManager authorizedClientManager,
            String clientRegistrationId,
            String principalName,
            String username,
            String password) {
        this.authorizedClientManager = authorizedClientManager;
        this.clientRegistrationId = clientRegistrationId;
        this.principalName = principalName;
        this.username = username;
        this.password = password;
    }

    @Override
    @NonNull
    public ClientHttpResponse intercept(
            @NonNull HttpRequest request,
            @NonNull byte[] body,
            @NonNull ClientHttpRequestExecution execution)
            throws IOException {

        OAuth2AuthorizeRequest authorizeRequest =
                OAuth2AuthorizeRequest.withClientRegistrationId(clientRegistrationId)
                        .principal(principalName)
                        .attributes(
                                attrs -> {
                                    attrs.put(
                                            OAuth2AuthorizationContext.USERNAME_ATTRIBUTE_NAME,
                                            username);
                                    attrs.put(
                                            OAuth2AuthorizationContext.PASSWORD_ATTRIBUTE_NAME,
                                            password);
                                })
                        .build();

        OAuth2AuthorizedClient authorizedClient =
                authorizedClientManager.authorize(authorizeRequest);

        if (authorizedClient == null) {
            throw new IllegalStateException(
                    "Cannot obtain OAuth2 token for " + clientRegistrationId);
        }

        String accessToken = authorizedClient.getAccessToken().getTokenValue();

        HttpHeaders headers = request.getHeaders();
        headers.setBearerAuth(accessToken);

        return execution.execute(request, body);
    }
}

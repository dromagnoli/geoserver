package org.geoserver.eumetsat.pinning.auth;

import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;

public class Wso2RestTemplateInterceptor implements ClientHttpRequestInterceptor {

    private final OAuth2AuthorizedClientManager authorizedClientManager;
    private final String clientRegistrationId;
    private final String principalName;

    public Wso2RestTemplateInterceptor(
            OAuth2AuthorizedClientManager authorizedClientManager,
            String clientRegistrationId,
            String principalName) {
        this.authorizedClientManager = authorizedClientManager;
        this.clientRegistrationId = clientRegistrationId;
        this.principalName = principalName;
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
                        .build();

        OAuth2AuthorizedClient authorizedClient =
                authorizedClientManager.authorize(authorizeRequest);

        if (authorizedClient == null) {
            throw new IllegalStateException("Cannot obtain WSO2 token for " + clientRegistrationId);
        }

        String accessToken = authorizedClient.getAccessToken().getTokenValue();

        HttpHeaders headers = request.getHeaders();
        headers.setBearerAuth(accessToken);

        return execution.execute(request, body);
    }
}

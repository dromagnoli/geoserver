package org.geoserver.eumetsat.pinning.auth;

public class AuthClientProperties {

    private final String registrationId;
    private final String tokenUri;
    private final String clientId;
    private final String clientSecret;
    private final String username;
    private final String password;

    private AuthClientProperties(Builder builder) {
        this.registrationId = builder.registrationId;
        this.tokenUri = builder.tokenUri;
        this.clientId = builder.clientId;
        this.clientSecret = builder.clientSecret;
        this.username = builder.username;
        this.password = builder.password;
    }

    public String getRegistrationId() {
        return registrationId;
    }

    public String tokenUri() {
        return tokenUri;
    }

    public String clientId() {
        return clientId;
    }

    public String clientSecret() {
        return clientSecret;
    }

    public String username() {
        return username;
    }

    public String password() {
        return password;
    }

    public static class Builder {
        private String registrationId;
        private String tokenUri;
        private String clientId;
        private String clientSecret;
        private String username;
        private String password;

        public Builder registrationId(String registrationId) {
            this.registrationId = registrationId;
            return this;
        }

        public Builder tokenUri(String tokenUri) {
            this.tokenUri = tokenUri;
            return this;
        }

        public Builder clientId(String clientId) {
            this.clientId = clientId;
            return this;
        }

        public Builder clientSecret(String clientSecret) {
            this.clientSecret = clientSecret;
            return this;
        }

        public Builder username(String username) {
            this.username = username;
            return this;
        }

        public Builder password(String password) {
            this.password = password;
            return this;
        }

        public AuthClientProperties build() {
            return new AuthClientProperties(this);
        }
    }

    public static Builder builder() {
        return new Builder();
    }
}

package com.mymail.integration.google;

/** Shared protocol values used by authorization, token exchange, and mapping. */
public final class GoogleOAuthConstants {
    public static final String AUTHORIZATION_URL = "https://accounts.google.com/o/oauth2/v2/auth";
    public static final String TOKEN_URL = "https://oauth2.googleapis.com/token";
    public static final String REVOCATION_URL = "https://oauth2.googleapis.com/revoke";
    public static final String PROFILE_URL = "https://gmail.googleapis.com/gmail/v1/users/me/profile";

    public static final String ACCESS_TOKEN = "access_token";
    public static final String REFRESH_TOKEN = "refresh_token";
    public static final String EXPIRES_IN = "expires_in";
    public static final String TOKEN_TYPE = "token_type";
    public static final String SCOPE = "scope";
    public static final String BEARER = "Bearer";
    public static final String PKCE_METHOD = "S256";
    public static final int TRANSACTION_LIFETIME_SECONDS = 600;

    private GoogleOAuthConstants() {
    }
}

package com.genzenglish.partner.auth;

import java.util.UUID;

import org.springframework.security.oauth2.jwt.Jwt;

import com.genzenglish.partner.common.ApiException;

public final class CurrentUser {

    private CurrentUser() {
    }

    /** The user ID carried in the access token's subject. */
    public static UUID id(Jwt jwt) {
        if (jwt == null) {
            throw ApiException.unauthorized("unauthorized", "Your session has ended. Sign in again.");
        }
        try {
            return UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException e) {
            throw ApiException.unauthorized("unauthorized", "Your session has ended. Sign in again.");
        }
    }
}

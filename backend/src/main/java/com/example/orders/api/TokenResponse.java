package com.example.orders.api;

import com.example.orders.security.IssuedToken;

/** Response of a successful login. {@code expiresIn} is the token lifetime in seconds. */
public record TokenResponse(String accessToken, String tokenType, long expiresIn) {

    static TokenResponse from(IssuedToken token) {
        return new TokenResponse(token.value(), "Bearer", token.expiresInSeconds());
    }
}

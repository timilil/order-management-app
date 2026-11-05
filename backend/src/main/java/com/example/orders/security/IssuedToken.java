package com.example.orders.security;

/** A signed access token and how long it is valid, in seconds. */
public record IssuedToken(String value, long expiresInSeconds) {
}

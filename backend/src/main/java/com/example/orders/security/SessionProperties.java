package com.example.orders.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Session settings from {@code app.security.session.*}. A session lasts a fixed time from login. */
@ConfigurationProperties(prefix = "app.security.session")
public record SessionProperties(int expirationMinutes) {

    public SessionProperties {
        if (expirationMinutes <= 0) {
            throw new IllegalArgumentException("app.security.session.expiration-minutes must be positive");
        }
    }
}

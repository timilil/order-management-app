package com.example.orders.error;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.BadCredentialsException;

/** The handlers that integration tests cannot trigger easily: lost updates and unexpected failures. */
class GlobalExceptionHandlerTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler(clock);
    private final MockHttpServletRequest request = new MockHttpServletRequest("PATCH", "/api/orders/x/status");

    @Test
    void optimisticLockFailureIs409() {
        ResponseEntity<Object> response = handler.concurrentModification(request);

        ErrorResponse body = (ErrorResponse) response.getBody();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(body.error()).isEqualTo("CONCURRENT_MODIFICATION");
        assertThat(body.path()).isEqualTo("/api/orders/x/status");
        assertThat(body.timestamp()).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"));
    }

    @Test
    void unexpectedErrorIs500WithoutAnyDetail() {
        Exception secret = new IllegalStateException(
                "could not execute statement [select * from orders] at org.postgresql.Driver password=hunter2");

        ResponseEntity<Object> response = handler.unexpected(secret, request);

        ErrorResponse body = (ErrorResponse) response.getBody();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(body.error()).isEqualTo("INTERNAL_ERROR");
        assertThat(body.toString()).doesNotContain("select", "postgresql", "hunter2", "IllegalStateException");
    }

    @Test
    void accessDeniedThrownByTheServiceIs403NotA500() {
        ResponseEntity<Object> response = handler.accessDenied(request);

        ErrorResponse body = (ErrorResponse) response.getBody();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(body.error()).isEqualTo("FORBIDDEN");
        assertThat(body.message()).isEqualTo("You do not have permission to perform this operation.");
    }

    @Test
    void authenticationFailureIs401WithBearerChallenge() {
        ResponseEntity<Object> response = handler.authenticationFailed(new BadCredentialsException("x"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getHeaders().getFirst("WWW-Authenticate")).isEqualTo("Bearer");
        assertThat(((ErrorResponse) response.getBody()).message()).isEqualTo("Invalid username or password.");
    }
}

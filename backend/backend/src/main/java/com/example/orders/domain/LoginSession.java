package com.example.orders.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * A login session. The ID is the SHA-256 hash of the token the client holds, so the table never contains a
 * usable token. Deleting the user deletes the session (database cascade).
 */
@Entity
@Table(name = "sessions")
public class LoginSession {

    @Id
    @Column(name = "token_hash", nullable = false, length = 64)
    private String id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected LoginSession() {
        // required by JPA
    }

    public LoginSession(String id, UUID userId, Instant createdAt, Instant expiresAt) {
        this.id = id;
        this.userId = userId;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public String getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}

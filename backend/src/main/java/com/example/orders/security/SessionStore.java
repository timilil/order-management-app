package com.example.orders.security;

import com.example.orders.domain.LoginSession;
import com.example.orders.repository.SessionRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Login sessions in the database. The client gets a random token. Only the SHA-256 hash of it (the
 * "session ID") is stored, so reading the table does not reveal any usable token. The token carries no
 * data: who the user is and what role they have is always read from the database.
 */
@Component
public class SessionStore {

    /** 256 bits from a secure random generator, which cannot be guessed. */
    private static final int TOKEN_BYTES = 32;

    private final SessionRepository sessions;
    private final Clock clock;
    private final Duration lifetime;
    private final SecureRandom random = new SecureRandom();

    SessionStore(SessionRepository sessions, Clock clock, SessionProperties properties) {
        this.sessions = sessions;
        this.clock = clock;
        this.lifetime = Duration.ofMinutes(properties.expirationMinutes());
    }

    /** The stored identifier of a session: the SHA-256 hash of the token, as hex. */
    public static String sessionId(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by the Java platform", e);
        }
    }

    /** Starts a session for the user. Also removes sessions that have expired, so the table does not grow. */
    public IssuedToken create(UUID userId) {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant now = Instant.now(clock);

        sessions.deleteExpired(now);
        sessions.save(new LoginSession(sessionId(token), userId, now, now.plus(lifetime)));
        return new IssuedToken(token, lifetime.toSeconds());
    }

    /** The user of a session that exists and has not expired. */
    public Optional<UUID> findUserId(String sessionId) {
        return sessions.findByIdAndExpiresAtAfter(sessionId, Instant.now(clock)).map(LoginSession::getUserId);
    }

    /** Ends the session. Does nothing if it does not exist. */
    public void delete(String sessionId) {
        sessions.deleteById(sessionId);
    }
}

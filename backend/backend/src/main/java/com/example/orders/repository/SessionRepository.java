package com.example.orders.repository;

import com.example.orders.domain.LoginSession;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/** Database access for login sessions. Sessions are identified by the hash of their token. */
public interface SessionRepository extends JpaRepository<LoginSession, String> {

    /** A session that exists and expires after {@code now}. */
    Optional<LoginSession> findByIdAndExpiresAtAfter(String id, Instant now);

    /** Removes all sessions that expired before {@code now}. */
    @Transactional
    @Modifying
    @Query("delete from LoginSession s where s.expiresAt < :now")
    int deleteExpired(@Param("now") Instant now);
}

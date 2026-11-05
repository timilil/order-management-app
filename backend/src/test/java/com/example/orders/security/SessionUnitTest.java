package com.example.orders.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.orders.domain.Role;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Plain unit tests: settings validation, the session ID, and what the authenticated user exposes. */
class SessionUnitTest {

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void sessionLifetimeMustBePositive(int minutes) {
        assertThatThrownBy(() -> new SessionProperties(minutes))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expiration-minutes");
    }

    @Test
    void sessionIdIsTheSha256HexOfTheToken() {
        // SHA-256("abc")
        assertThat(SessionStore.sessionId("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void sessionIdDiffersForDifferentTokensAndIsNotTheToken() {
        assertThat(SessionStore.sessionId("token-a")).isNotEqualTo(SessionStore.sessionId("token-b"));
        assertThat(SessionStore.sessionId("token-a")).isNotEqualTo("token-a").hasSize(64);
    }

    @Test
    void userHasExactlyTheAuthorityOfItsRole() {
        AppUser user = AppUser.forSession(UUID.randomUUID(), "admin1", Role.ADMIN, "sid");

        assertThat(user.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_ADMIN");
    }

    @Test
    void sessionUserHasNoPasswordHashButLoginUserHas() {
        UUID id = UUID.randomUUID();

        assertThat(AppUser.forSession(id, "u", Role.CUSTOMER, "sid").getPassword()).isNull();
        assertThat(AppUser.forLogin(id, "u", "hash", Role.CUSTOMER).getPassword()).isEqualTo("hash");
        assertThat(AppUser.forLogin(id, "u", "hash", Role.CUSTOMER).getSessionId()).isNull();
    }

    @Test
    void userDoesNotPrintItsPasswordHash() {
        assertThat(AppUser.forLogin(UUID.randomUUID(), "u", "secret-hash", Role.CUSTOMER).toString())
                .doesNotContain("secret-hash");
    }
}

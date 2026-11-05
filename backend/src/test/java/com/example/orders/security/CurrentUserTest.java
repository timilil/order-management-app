package com.example.orders.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.orders.domain.Role;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.jwt.Jwt;

class CurrentUserTest {

    private static Jwt token(String subject, String role) {
        Jwt.Builder builder = Jwt.withTokenValue("t")
                .header("alg", "HS256")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60));
        if (subject != null) {
            builder.subject(subject);
        }
        if (role != null) {
            builder.claim(CurrentUser.ROLE_CLAIM, role);
        }
        return builder.build();
    }

    @Test
    void readsIdFromSubjectAndRoleFromClaim() {
        UUID id = UUID.randomUUID();

        CurrentUser user = CurrentUser.from(token(id.toString(), "SUPPORT"));

        assertThat(user.id()).isEqualTo(id);
        assertThat(user.role()).isEqualTo(Role.SUPPORT);
    }

    @Test
    void rejectsMissingSubject() {
        assertThatThrownBy(() -> CurrentUser.from(token(null, "CUSTOMER"))).isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void rejectsMissingRole() {
        assertThatThrownBy(() -> CurrentUser.from(token(UUID.randomUUID().toString(), null)))
                .isInstanceOf(BadCredentialsException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ROOT", "customer", ""})
    void rejectsUnknownRole(String role) {
        assertThatThrownBy(() -> CurrentUser.from(token(UUID.randomUUID().toString(), role)))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void rejectsSubjectThatIsNotAUuid() {
        assertThatThrownBy(() -> CurrentUser.from(token("not-a-uuid", "CUSTOMER")))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void rejectsMissingToken() {
        assertThatThrownBy(() -> CurrentUser.from(null)).isInstanceOf(BadCredentialsException.class);
    }
}

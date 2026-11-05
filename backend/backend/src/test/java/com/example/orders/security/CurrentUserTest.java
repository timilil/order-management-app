package com.example.orders.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.orders.domain.Role;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;

class CurrentUserTest {

    @Test
    void takesIdAndRoleFromTheAuthenticatedUser() {
        UUID id = UUID.randomUUID();

        CurrentUser user = CurrentUser.from(AppUser.forSession(id, "support1", Role.SUPPORT, "session-id"));

        assertThat(user.id()).isEqualTo(id);
        assertThat(user.role()).isEqualTo(Role.SUPPORT);
    }

    @Test
    void rejectsMissingUser() {
        assertThatThrownBy(() -> CurrentUser.from(null)).isInstanceOf(BadCredentialsException.class);
    }
}

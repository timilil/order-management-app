package com.example.orders.security;

import com.example.orders.repository.UserRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/** Spring Security's user lookup, backed by the {@code users} table. */
@Service
class AppUserDetailsService implements UserDetailsService {

    private final UserRepository users;

    AppUserDetailsService(UserRepository users) {
        this.users = users;
    }

    /** Used at login, with the password hash. */
    @Override
    public AppUser loadUserByUsername(String username) {
        return users.findByUsername(username)
                .map(u -> AppUser.forLogin(u.getId(), u.getUsername(), u.getPasswordHash(), u.getRole()))
                .orElseThrow(() -> new UsernameNotFoundException("Unknown user"));
    }

    /** Used on every request: the current user data, so role changes apply at once. */
    Optional<AppUser> loadForSession(UUID userId, String sessionId) {
        return users.findById(userId).map(u -> AppUser.forSession(u.getId(), u.getUsername(), u.getRole(), sessionId));
    }
}

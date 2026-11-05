package com.example.orders.security;

import com.example.orders.domain.Role;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;

/**
 * The authenticated user, as Spring Security sees it. Used by {@code @PreAuthorize} (for example
 * {@code principal.id}) and read by the controllers. At login it carries the password hash so the password
 * can be checked. On later requests it is rebuilt from the database for the session, without the hash, and
 * carries the {@link #getSessionId() session ID}.
 */
public final class AppUser implements UserDetails, OAuth2AuthenticatedPrincipal {

    private final UUID id;
    private final String username;
    private final String passwordHash;
    private final Role role;
    private final String sessionId;

    private AppUser(UUID id, String username, String passwordHash, Role role, String sessionId) {
        this.id = id;
        this.username = username;
        this.passwordHash = passwordHash;
        this.role = role;
        this.sessionId = sessionId;
    }

    /** For checking a login: includes the password hash. */
    static AppUser forLogin(UUID id, String username, String passwordHash, Role role) {
        return new AppUser(id, username, passwordHash, role, null);
    }

    /** For an authenticated request: no password hash, but the session it belongs to. */
    static AppUser forSession(UUID id, String username, Role role, String sessionId) {
        return new AppUser(id, username, null, role, sessionId);
    }

    public UUID getId() {
        return id;
    }

    public Role getRole() {
        return role;
    }

    /** The session this request authenticated with, or {@code null} while logging in. */
    public String getSessionId() {
        return sessionId;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public Map<String, Object> getAttributes() {
        return Map.of("id", id.toString(), "username", username, "role", role.name());
    }

    @Override
    public String getName() {
        return username;
    }

    @Override
    public String toString() {
        return "AppUser[" + username + ", " + role + "]";
    }
}

package com.example.orders.security;

import java.util.UUID;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.server.resource.introspection.BadOpaqueTokenException;
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector;
import org.springframework.stereotype.Component;

/**
 * Authenticates every request: looks the bearer token up as a session in the database and builds the
 * {@link AppUser} from the current user data. An unknown, expired or logged-out session is rejected, which
 * becomes a 401.
 */
@Component
class SessionTokenIntrospector implements OpaqueTokenIntrospector {

    private final SessionStore sessions;
    private final AppUserDetailsService userDetails;

    SessionTokenIntrospector(SessionStore sessions, AppUserDetailsService userDetails) {
        this.sessions = sessions;
        this.userDetails = userDetails;
    }

    @Override
    public OAuth2AuthenticatedPrincipal introspect(String token) {
        String sessionId = SessionStore.sessionId(token);
        UUID userId = sessions.findUserId(sessionId)
                .orElseThrow(() -> new BadOpaqueTokenException("Unknown or expired session"));
        return userDetails.loadForSession(userId, sessionId)
                .orElseThrow(() -> new BadOpaqueTokenException("Session user no longer exists"));
    }
}

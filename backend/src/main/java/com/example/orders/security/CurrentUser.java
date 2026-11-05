package com.example.orders.security;

import com.example.orders.domain.Role;
import java.util.UUID;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * The authenticated caller. Built only from the validated token: the subject is the user ID and the
 * {@value #ROLE_CLAIM} claim is the role. Nothing here ever comes from a request body.
 */
public record CurrentUser(UUID id, Role role) {

    public static final String ROLE_CLAIM = "role";

    /**
     * @throws BadCredentialsException if the token has no valid subject or role (handled as 401)
     */
    public static CurrentUser from(Jwt jwt) {
        String subject = jwt == null ? null : jwt.getSubject();
        String role = jwt == null ? null : jwt.getClaimAsString(ROLE_CLAIM);
        if (subject == null || role == null) {
            throw new BadCredentialsException("Token has no subject or role");
        }
        try {
            return new CurrentUser(UUID.fromString(subject), Role.valueOf(role));
        } catch (IllegalArgumentException e) {
            throw new BadCredentialsException("Token has an invalid subject or role");
        }
    }
}

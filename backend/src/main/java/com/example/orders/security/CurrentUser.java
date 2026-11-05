package com.example.orders.security;

import com.example.orders.domain.Role;
import java.util.UUID;
import org.springframework.security.authentication.BadCredentialsException;

/**
 * The authenticated caller, as the service layer sees it. Built only from the authenticated user (loaded
 * from the database for the session). Nothing here ever comes from a request body.
 */
public record CurrentUser(UUID id, Role role) {

    /**
     * @throws BadCredentialsException if there is no authenticated user (handled as 401)
     */
    public static CurrentUser from(AppUser user) {
        if (user == null) {
            throw new BadCredentialsException("No authenticated user");
        }
        return new CurrentUser(user.getId(), user.getRole());
    }
}

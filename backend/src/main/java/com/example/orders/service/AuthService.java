package com.example.orders.service;

import com.example.orders.security.AppUser;
import com.example.orders.security.IssuedToken;
import com.example.orders.security.SessionStore;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

/** Checks a username and password and starts or ends a session. Authorization is not decided here. */
@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final SessionStore sessions;

    public AuthService(AuthenticationManager authenticationManager, SessionStore sessions) {
        this.authenticationManager = authenticationManager;
        this.sessions = sessions;
    }

    /**
     * @throws BadCredentialsException for an unknown user and a wrong password alike, so the response does
     *     not reveal which usernames exist
     */
    public IssuedToken login(String username, String password) {
        // No transaction is held open here: BCrypt is deliberately slow.
        Authentication authentication =
                authenticationManager.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(username, password));
        AppUser user = (AppUser) authentication.getPrincipal();
        return sessions.create(user.getId());
    }

    /** Ends the session. Safe to call for a session that is already gone. */
    public void logout(String sessionId) {
        sessions.delete(sessionId);
    }
}

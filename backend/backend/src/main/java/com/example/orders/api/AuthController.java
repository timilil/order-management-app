package com.example.orders.api;

import com.example.orders.security.AppUser;
import com.example.orders.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    @Operation(summary = "Exchange a username and password for a bearer token")
    @ApiResponse(responseCode = "400", description = "Missing or oversized username or password")
    @ApiResponse(responseCode = "401", description = "Wrong username or password")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return TokenResponse.from(authService.login(request.username(), request.password()));
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "End the session of the bearer token used for this request")
    @ApiResponse(responseCode = "204", description = "The token can no longer be used")
    @ApiResponse(responseCode = "401", description = "Missing, unknown, expired or already logged-out token")
    public void logout(@AuthenticationPrincipal AppUser user) {
        authService.logout(user.getSessionId());
    }
}

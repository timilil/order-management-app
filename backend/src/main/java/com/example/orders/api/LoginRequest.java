package com.example.orders.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request body for {@code POST /api/auth/login}. The password limit is BCrypt's 72-byte input limit. */
public record LoginRequest(
        @NotBlank @Size(max = 100) @Schema(example = "customer1") String username,
        @NotBlank @Size(max = 72) @Schema(example = "dev-password-1") String password) {
}

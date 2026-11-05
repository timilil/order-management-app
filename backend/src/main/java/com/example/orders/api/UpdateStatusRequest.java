package com.example.orders.api;

import com.example.orders.domain.OrderStatus;
import jakarta.validation.constraints.NotNull;

/** Request body for {@code PATCH /api/orders/{id}/status}. */
public record UpdateStatusRequest(@NotNull OrderStatus status) {
}

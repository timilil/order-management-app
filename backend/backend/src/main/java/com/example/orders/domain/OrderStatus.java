package com.example.orders.domain;

import java.util.Optional;

/**
 * Order lifecycle: CREATED -> CONFIRMED -> SHIPPED -> DELIVERED.
 *
 * <p>Transitions only move forward, one step at a time. Skipping, going back and cancelling are not
 * allowed. This enum only knows which transitions are valid. Which user role may request a transition
 * is an authorization rule and is enforced elsewhere (see design document, section 3).
 */
public enum OrderStatus {
    CREATED,
    CONFIRMED,
    SHIPPED,
    DELIVERED;

    /** The only status this status may move to, or empty when the order is in its final status. */
    public Optional<OrderStatus> next() {
        return switch (this) {
            case CREATED -> Optional.of(CONFIRMED);
            case CONFIRMED -> Optional.of(SHIPPED);
            case SHIPPED -> Optional.of(DELIVERED);
            case DELIVERED -> Optional.empty();
        };
    }

    public boolean canTransitionTo(OrderStatus target) {
        return next().filter(allowed -> allowed == target).isPresent();
    }
}

package com.example.orders.error;

import java.util.UUID;

/**
 * The order does not exist, or the caller is not allowed to know about it. Both cases use this same
 * exception so a customer cannot find out which order IDs exist.
 */
public class OrderNotFoundException extends RuntimeException {

    public OrderNotFoundException(UUID orderId) {
        super("Order " + orderId + " not found");
    }
}

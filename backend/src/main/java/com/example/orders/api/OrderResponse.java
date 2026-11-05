package com.example.orders.api;

import com.example.orders.domain.OrderStatus;
import com.example.orders.service.OrderView;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** JSON representation of an order. Entities are never returned directly. */
public record OrderResponse(
        UUID id,
        UUID customerId,
        OrderStatus status,
        BigDecimal totalAmount,
        Instant createdAt,
        Instant updatedAt,
        List<Item> items) {

    public record Item(
            String productId, String productName, int quantity, BigDecimal unitPrice, BigDecimal lineTotal) {
    }

    static OrderResponse from(OrderView view) {
        return new OrderResponse(
                view.id(),
                view.customerId(),
                view.status(),
                view.totalAmount(),
                view.createdAt(),
                view.updatedAt(),
                view.items().stream()
                        .map(i -> new Item(
                                i.productId(), i.productName(), i.quantity(), i.unitPrice(), i.lineTotal()))
                        .toList());
    }
}

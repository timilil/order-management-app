package com.example.orders.service;

import com.example.orders.domain.Order;
import com.example.orders.domain.OrderItem;
import com.example.orders.domain.OrderStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Immutable copy of an order, created inside the transaction. Returning this instead of the entity
 * keeps lazy loading and JPA state out of the API layer.
 */
public record OrderView(
        UUID id,
        UUID customerId,
        OrderStatus status,
        BigDecimal totalAmount,
        Instant createdAt,
        Instant updatedAt,
        List<ItemView> items) {

    public record ItemView(
            String productId, String productName, int quantity, BigDecimal unitPrice, BigDecimal lineTotal) {

        static ItemView from(OrderItem item) {
            return new ItemView(
                    item.getProductId(),
                    item.getProductName(),
                    item.getQuantity(),
                    item.getUnitPrice(),
                    item.lineTotal());
        }
    }

    static OrderView from(Order order) {
        return new OrderView(
                order.getId(),
                order.getCustomerId(),
                order.getStatus(),
                order.getTotalAmount(),
                order.getCreatedAt(),
                order.getUpdatedAt(),
                order.getItems().stream().map(ItemView::from).toList());
    }
}

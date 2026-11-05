package com.example.orders.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * An order with its items. The total is always calculated here from the items, never taken from the
 * client. Status changes go through {@link #changeStatus} so the lifecycle rules cannot be bypassed.
 */
@Entity
@Table(name = "orders")
public class Order {

    public static final int MAX_ITEMS = 100;

    @Id
    private UUID id;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    @Column(name = "total_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalAmount;

    /** Optimistic locking: a concurrent update of the same order fails instead of overwriting. */
    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    protected Order() {
        // required by JPA
    }

    private Order(UUID customerId, List<OrderItem> items, Instant now) {
        this.id = UUID.randomUUID();
        this.customerId = customerId;
        this.status = OrderStatus.CREATED;
        this.createdAt = now;
        this.updatedAt = now;
        BigDecimal total = BigDecimal.ZERO;
        for (OrderItem item : items) {
            item.attachTo(this);
            this.items.add(item);
            total = total.add(item.lineTotal());
        }
        this.totalAmount = total;
    }

    /** Creates a new order in status CREATED for the given customer. */
    public static Order create(UUID customerId, List<OrderItem> items, Instant now) {
        if (customerId == null) {
            throw new IllegalArgumentException("customerId is required");
        }
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("An order needs at least one item");
        }
        if (items.size() > MAX_ITEMS) {
            throw new IllegalArgumentException("An order can have at most " + MAX_ITEMS + " items");
        }
        return new Order(customerId, items, now);
    }

    /**
     * Moves the order to the given status.
     *
     * @throws InvalidStatusTransitionException if the target is not the next status in the lifecycle
     */
    public void changeStatus(OrderStatus target, Instant now) {
        if (!status.canTransitionTo(target)) {
            throw new InvalidStatusTransitionException(status, target);
        }
        this.status = target;
        this.updatedAt = now;
    }

    public boolean isOwnedBy(UUID userId) {
        return customerId.equals(userId);
    }

    public UUID getId() {
        return id;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public Long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public List<OrderItem> getItems() {
        return Collections.unmodifiableList(items);
    }
}

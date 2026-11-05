package com.example.orders.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OrderTest {

    private static final UUID CUSTOMER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final Instant T0 = Instant.parse("2026-01-01T10:00:00Z");
    private static final Instant T1 = Instant.parse("2026-01-01T11:00:00Z");

    private static OrderItem keyboard() {
        return new OrderItem("PROD-101", "Keyboard", 2, new BigDecimal("49.99"));
    }

    private static OrderItem mouse() {
        return new OrderItem("PROD-102", "Mouse", 1, new BigDecimal("19.99"));
    }

    private static Order newOrder() {
        return Order.create(CUSTOMER_ID, List.of(keyboard(), mouse()), T0);
    }

    // --- creation and total calculation ---

    @Test
    void calculatesTotalFromItems() {
        // The example from the assignment: 2 x 49.99 + 1 x 19.99
        Order order = newOrder();

        assertThat(order.getTotalAmount()).isEqualByComparingTo("119.97");
    }

    @Test
    void totalOfSingleItemIsItsLineTotal() {
        Order order = Order.create(CUSTOMER_ID, List.of(keyboard()), T0);

        assertThat(order.getTotalAmount()).isEqualByComparingTo("99.98");
    }

    @Test
    void totalIsExactForPricesThatBreakBinaryFloatingPoint() {
        // 3 x 0.10 would be 0.30000000000000004 as a double
        Order order = Order.create(CUSTOMER_ID, List.of(new OrderItem("P", "N", 3, new BigDecimal("0.10"))), T0);

        assertThat(order.getTotalAmount()).isEqualByComparingTo("0.30");
        assertThat(order.getTotalAmount().scale()).isEqualTo(2);
    }

    @Test
    void totalOfLargestAllowedOrderFitsTheDatabaseColumn() {
        List<OrderItem> items = new ArrayList<>();
        for (int i = 0; i < Order.MAX_ITEMS; i++) {
            items.add(new OrderItem("P" + i, "N", OrderItem.MAX_QUANTITY, OrderItem.MAX_UNIT_PRICE));
        }

        Order order = Order.create(CUSTOMER_ID, items, T0);

        // NUMERIC(15,2) holds up to 9,999,999,999,999.99
        assertThat(order.getTotalAmount()).isEqualByComparingTo("9999999999000.00");
        assertThat(order.getTotalAmount()).isLessThan(new BigDecimal("10000000000000"));
    }

    @Test
    void newOrderStartsInCreatedStatusForTheGivenCustomer() {
        Order order = newOrder();

        assertThat(order.getId()).isNotNull();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CREATED);
        assertThat(order.getCustomerId()).isEqualTo(CUSTOMER_ID);
        assertThat(order.getCreatedAt()).isEqualTo(T0);
        assertThat(order.getUpdatedAt()).isEqualTo(T0);
        assertThat(order.getItems()).hasSize(2);
    }

    @Test
    void itemsCannotBeModifiedFromOutside() {
        Order order = newOrder();

        assertThatThrownBy(() -> order.getItems().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    // --- validation ---

    @Test
    void rejectsOrderWithoutItems() {
        assertThatThrownBy(() -> Order.create(CUSTOMER_ID, List.of(), T0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one item");
        assertThatThrownBy(() -> Order.create(CUSTOMER_ID, null, T0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsOrderWithTooManyItems() {
        List<OrderItem> items = new ArrayList<>();
        for (int i = 0; i <= Order.MAX_ITEMS; i++) {
            items.add(mouse());
        }

        assertThatThrownBy(() -> Order.create(CUSTOMER_ID, items, T0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at most");
    }

    @Test
    void acceptsExactlyTheMaximumNumberOfItems() {
        List<OrderItem> items = new ArrayList<>();
        for (int i = 0; i < Order.MAX_ITEMS; i++) {
            items.add(mouse());
        }

        assertThat(Order.create(CUSTOMER_ID, items, T0).getItems()).hasSize(Order.MAX_ITEMS);
    }

    @Test
    void rejectsOrderWithoutCustomer() {
        assertThatThrownBy(() -> Order.create(null, List.of(mouse()), T0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("customerId");
    }

    // --- ownership ---

    @Test
    void knowsWhoOwnsIt() {
        Order order = newOrder();

        assertThat(order.isOwnedBy(CUSTOMER_ID)).isTrue();
        assertThat(order.isOwnedBy(UUID.randomUUID())).isFalse();
    }

    // --- status transitions ---

    @Test
    void movesThroughTheWholeLifecycleAndUpdatesTimestamp() {
        Order order = newOrder();

        order.changeStatus(OrderStatus.CONFIRMED, T1);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(order.getUpdatedAt()).isEqualTo(T1);

        order.changeStatus(OrderStatus.SHIPPED, T1);
        order.changeStatus(OrderStatus.DELIVERED, T1);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.DELIVERED);
        assertThat(order.getCreatedAt()).isEqualTo(T0);
    }

    @Test
    void rejectsSkippingAStatusAndLeavesOrderUnchanged() {
        Order order = newOrder();

        assertThatThrownBy(() -> order.changeStatus(OrderStatus.SHIPPED, T1))
                .isInstanceOf(InvalidStatusTransitionException.class)
                .hasMessage("Cannot change order status from CREATED to SHIPPED");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CREATED);
        assertThat(order.getUpdatedAt()).isEqualTo(T0);
    }

    @Test
    void rejectsGoingBackwards() {
        Order order = newOrder();
        order.changeStatus(OrderStatus.CONFIRMED, T1);

        assertThatThrownBy(() -> order.changeStatus(OrderStatus.CREATED, T1))
                .isInstanceOf(InvalidStatusTransitionException.class);
    }

    @Test
    void rejectsRepeatingTheCurrentStatus() {
        Order order = newOrder();

        assertThatThrownBy(() -> order.changeStatus(OrderStatus.CREATED, T1))
                .isInstanceOf(InvalidStatusTransitionException.class);
    }

    @Test
    void deliveredOrderCannotChangeAnymore() {
        Order order = newOrder();
        order.changeStatus(OrderStatus.CONFIRMED, T1);
        order.changeStatus(OrderStatus.SHIPPED, T1);
        order.changeStatus(OrderStatus.DELIVERED, T1);

        for (OrderStatus target : OrderStatus.values()) {
            assertThatThrownBy(() -> order.changeStatus(target, T1))
                    .isInstanceOf(InvalidStatusTransitionException.class);
        }
    }

    @Test
    void transitionExceptionExposesBothStatuses() {
        Order order = newOrder();

        assertThatThrownBy(() -> order.changeStatus(OrderStatus.DELIVERED, T1))
                .isInstanceOfSatisfying(InvalidStatusTransitionException.class, e -> {
                    assertThat(e.getFrom()).isEqualTo(OrderStatus.CREATED);
                    assertThat(e.getTo()).isEqualTo(OrderStatus.DELIVERED);
                });
    }
}

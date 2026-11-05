package com.example.orders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

/** Verifies that the Liquibase migrations create the expected schema and enforce its constraints. */
class DatabaseSchemaTests extends AbstractIntegrationTest {

    private static final UUID CUSTOMER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Test
    void seedUsersExistInTestContext() {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM users WHERE username IN ('customer1', 'customer2', 'support1', 'admin1')",
                Integer.class);
        assertThat(count).isEqualTo(4);
    }

    @Test
    void customerIdIsIndexed() {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM pg_indexes WHERE tablename = 'orders' AND indexname = 'idx_orders_customer_id'",
                Integer.class);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void rejectsOrderForUnknownCustomer() {
        assertThatThrownBy(() -> insertOrder(UUID.randomUUID(), UUID.randomUUID(), "CREATED", "10.00"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsInvalidOrderStatus() {
        assertThatThrownBy(() -> insertOrder(UUID.randomUUID(), CUSTOMER_ID, "CANCELLED", "10.00"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsItemWithZeroQuantity() {
        UUID orderId = UUID.randomUUID();
        insertOrder(orderId, CUSTOMER_ID, "CREATED", "10.00");
        assertThatThrownBy(() -> insertItem(orderId, 0, "10.00"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsItemWithNegativePrice() {
        UUID orderId = UUID.randomUUID();
        insertOrder(orderId, CUSTOMER_ID, "CREATED", "10.00");
        assertThatThrownBy(() -> insertItem(orderId, 1, "-1.00"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsItemWithoutOrder() {
        assertThatThrownBy(() -> insertItem(UUID.randomUUID(), 1, "10.00"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deletingOrderDeletesItsItems() {
        UUID orderId = UUID.randomUUID();
        insertOrder(orderId, CUSTOMER_ID, "CREATED", "20.00");
        insertItem(orderId, 2, "10.00");

        jdbc.update("DELETE FROM orders WHERE id = ?", orderId);

        Integer items = jdbc.queryForObject(
                "SELECT count(*) FROM order_items WHERE order_id = ?", Integer.class, orderId);
        assertThat(items).isZero();
    }

    private void insertOrder(UUID id, UUID customerId, String status, String total) {
        jdbc.update("""
                INSERT INTO orders (id, customer_id, status, total_amount, version, created_at, updated_at)
                VALUES (?, ?, ?, ?::numeric, 0, now(), now())
                """, id, customerId, status, total);
    }

    private void insertItem(UUID orderId, int quantity, String unitPrice) {
        jdbc.update("""
                INSERT INTO order_items (id, order_id, product_id, product_name, quantity, unit_price)
                VALUES (?, ?, 'PROD-101', 'Keyboard', ?, ?::numeric)
                """, UUID.randomUUID(), orderId, quantity, unitPrice);
    }
}

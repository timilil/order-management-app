package com.example.orders.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class OrderItemTest {

    @Test
    void calculatesLineTotalAsUnitPriceTimesQuantity() {
        OrderItem item = new OrderItem("PROD-101", "Keyboard", 2, new BigDecimal("49.99"));

        assertThat(item.lineTotal()).isEqualByComparingTo("99.98");
    }

    @Test
    void normalizesUnitPriceToTwoDecimals() {
        OrderItem item = new OrderItem("PROD-101", "Keyboard", 1, new BigDecimal("10.5"));

        assertThat(item.getUnitPrice()).hasToString("10.50");
    }

    @Test
    void acceptsBoundaryValues() {
        new OrderItem("P", "N", OrderItem.MIN_QUANTITY, OrderItem.MIN_UNIT_PRICE);
        new OrderItem("P", "N", OrderItem.MAX_QUANTITY, OrderItem.MAX_UNIT_PRICE);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 1001})
    void rejectsQuantityOutOfRange(int quantity) {
        assertThatThrownBy(() -> new OrderItem("PROD-101", "Keyboard", quantity, BigDecimal.TEN))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("quantity");
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "0.00", "0.009", "-5.00", "100000000.00"})
    void rejectsUnitPriceOutOfRange(String price) {
        assertThatThrownBy(() -> new OrderItem("PROD-101", "Keyboard", 1, new BigDecimal(price)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unitPrice");
    }

    @Test
    void rejectsMoreThanTwoDecimalPlaces() {
        assertThatThrownBy(() -> new OrderItem("PROD-101", "Keyboard", 1, new BigDecimal("1.234")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("decimal places");
    }

    @Test
    void acceptsTrailingZerosBeyondTwoDecimals() {
        OrderItem item = new OrderItem("PROD-101", "Keyboard", 1, new BigDecimal("1.2300"));

        assertThat(item.getUnitPrice()).hasToString("1.23");
    }

    @Test
    void rejectsMissingUnitPrice() {
        assertThatThrownBy(() -> new OrderItem("PROD-101", "Keyboard", 1, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void rejectsBlankProductIdAndName(String blank) {
        assertThatThrownBy(() -> new OrderItem(blank, "Keyboard", 1, BigDecimal.TEN))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("productId");
        assertThatThrownBy(() -> new OrderItem("PROD-101", blank, 1, BigDecimal.TEN))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("productName");
    }

    @Test
    void rejectsNullProductIdAndName() {
        assertThatThrownBy(() -> new OrderItem(null, "Keyboard", 1, BigDecimal.TEN))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new OrderItem("PROD-101", null, 1, BigDecimal.TEN))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

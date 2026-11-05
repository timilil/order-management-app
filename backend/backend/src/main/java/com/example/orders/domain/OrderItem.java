package com.example.orders.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

/** One line of an order: a product, how many were ordered and the price per unit. */
@Entity
@Table(name = "order_items")
public class OrderItem {

    public static final int MIN_QUANTITY = 1;
    public static final int MAX_QUANTITY = 1000;
    public static final BigDecimal MIN_UNIT_PRICE = new BigDecimal("0.01");
    public static final BigDecimal MAX_UNIT_PRICE = new BigDecimal("99999999.99");
    public static final int PRICE_SCALE = 2;

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(name = "product_id", nullable = false, length = 50)
    private String productId;

    @Column(name = "product_name", nullable = false, length = 200)
    private String productName;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "unit_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPrice;

    protected OrderItem() {
        // required by JPA
    }

    public OrderItem(String productId, String productName, int quantity, BigDecimal unitPrice) {
        if (productId == null || productId.isBlank() || productId.length() > 50) {
            throw new IllegalArgumentException("productId must be 1 to 50 characters");
        }
        if (productName == null || productName.isBlank() || productName.length() > 200) {
            throw new IllegalArgumentException("productName must be 1 to 200 characters");
        }
        if (quantity < MIN_QUANTITY || quantity > MAX_QUANTITY) {
            throw new IllegalArgumentException(
                    "quantity must be between " + MIN_QUANTITY + " and " + MAX_QUANTITY);
        }
        if (unitPrice == null) {
            throw new IllegalArgumentException("unitPrice is required");
        }
        if (unitPrice.stripTrailingZeros().scale() > PRICE_SCALE) {
            throw new IllegalArgumentException("unitPrice must have at most " + PRICE_SCALE + " decimal places");
        }
        if (unitPrice.compareTo(MIN_UNIT_PRICE) < 0 || unitPrice.compareTo(MAX_UNIT_PRICE) > 0) {
            throw new IllegalArgumentException(
                    "unitPrice must be between " + MIN_UNIT_PRICE + " and " + MAX_UNIT_PRICE);
        }
        this.id = UUID.randomUUID();
        this.productId = productId;
        this.productName = productName;
        this.quantity = quantity;
        this.unitPrice = unitPrice.setScale(PRICE_SCALE);
    }

    /** Unit price multiplied by quantity. Exact, because prices never have more than 2 decimals. */
    public BigDecimal lineTotal() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }

    /** Called by {@link Order} when the item is added to it. */
    void attachTo(Order order) {
        this.order = order;
    }

    public UUID getId() {
        return id;
    }

    public String getProductId() {
        return productId;
    }

    public String getProductName() {
        return productName;
    }

    public int getQuantity() {
        return quantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }
}

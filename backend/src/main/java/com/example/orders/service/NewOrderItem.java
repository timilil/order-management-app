package com.example.orders.service;

import java.math.BigDecimal;

/** One requested order line, as handed from the API to the service. Validation happens in the domain. */
public record NewOrderItem(String productId, String productName, int quantity, BigDecimal unitPrice) {
}

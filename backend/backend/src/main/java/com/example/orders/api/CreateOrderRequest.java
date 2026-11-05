package com.example.orders.api;

import com.example.orders.domain.Order;
import com.example.orders.domain.OrderItem;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

/**
 * Request body for creating an order. It has no customer ID, status or total: the customer comes from
 * the token and the server calculates the rest. Extra JSON fields such as {@code totalAmount} are ignored.
 */
public record CreateOrderRequest(
        @NotEmpty @Size(max = Order.MAX_ITEMS) List<@NotNull @Valid ItemRequest> items) {

    public record ItemRequest(
            @NotBlank @Size(max = 50) @Schema(example = "PROD-101") String productId,
            @NotBlank @Size(max = 200) @Schema(example = "Keyboard") String productName,
            @NotNull @Min(OrderItem.MIN_QUANTITY) @Max(OrderItem.MAX_QUANTITY) @Schema(example = "2")
                    Integer quantity,
            @NotNull
                    @DecimalMin("0.01")
                    @DecimalMax("99999999.99")
                    @Digits(integer = 8, fraction = 2)
                    @Schema(example = "49.99")
                    BigDecimal unitPrice) {
    }
}

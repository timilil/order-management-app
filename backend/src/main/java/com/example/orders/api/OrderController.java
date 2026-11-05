package com.example.orders.api;

import com.example.orders.security.CurrentUser;
import com.example.orders.service.NewOrderItem;
import com.example.orders.service.OrderService;
import com.example.orders.service.OrderView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/** HTTP mapping only. Identity comes from the validated token, rules live in {@link OrderService}. */
@RestController
@RequestMapping("/api/orders")
@Tag(name = "Orders")
public class OrderController {

    private final OrderService service;

    public OrderController(OrderService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(summary = "Create an order for the logged-in user (CUSTOMER, ADMIN)")
    @ApiResponse(responseCode = "201", description = "Order created, Location header points to it")
    @ApiResponse(responseCode = "400", description = "Invalid request body")
    @ApiResponse(responseCode = "403", description = "Role may not create orders")
    public ResponseEntity<OrderResponse> create(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateOrderRequest request) {
        var items = request.items().stream()
                .map(i -> new NewOrderItem(i.productId(), i.productName(), i.quantity(), i.unitPrice()))
                .toList();
        OrderView created = service.create(CurrentUser.from(jwt), items);
        URI location =
                ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").build(created.id());
        return ResponseEntity.created(location).body(OrderResponse.from(created));
    }

    @GetMapping
    @Operation(summary = "List all orders, paged (SUPPORT, ADMIN)")
    @ApiResponse(responseCode = "403", description = "Role may not list all orders")
    public PageResponse<OrderResponse> listAll(
            @AuthenticationPrincipal Jwt jwt, @ParameterObject Pageable pageable) {
        return PageResponse.from(
                service.listAll(CurrentUser.from(jwt), PageableSupport.apply(pageable))
                        .map(OrderResponse::from));
    }

    @GetMapping("/my")
    @Operation(summary = "List the logged-in user's own orders, paged")
    public PageResponse<OrderResponse> listMine(
            @AuthenticationPrincipal Jwt jwt, @ParameterObject Pageable pageable) {
        return PageResponse.from(
                service.listMine(CurrentUser.from(jwt), PageableSupport.apply(pageable))
                        .map(OrderResponse::from));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get one order. Customers can only see their own")
    @ApiResponse(responseCode = "404", description = "No such order, or it belongs to someone else")
    public OrderResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return OrderResponse.from(service.get(CurrentUser.from(jwt), id));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Move an order to its next status (SUPPORT, ADMIN)")
    @ApiResponse(responseCode = "403", description = "Role may not change status")
    @ApiResponse(responseCode = "404", description = "No such order")
    @ApiResponse(responseCode = "409", description = "Not the next status, or the order changed concurrently")
    public OrderResponse updateStatus(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateStatusRequest request) {
        return OrderResponse.from(service.updateStatus(CurrentUser.from(jwt), id, request.status()));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Permanently delete an order (ADMIN)")
    @ApiResponse(responseCode = "204", description = "Order deleted")
    @ApiResponse(responseCode = "403", description = "Role may not delete orders")
    @ApiResponse(responseCode = "404", description = "No such order")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        service.delete(CurrentUser.from(jwt), id);
        return ResponseEntity.noContent().build();
    }
}

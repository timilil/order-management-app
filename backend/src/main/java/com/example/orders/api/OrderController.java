package com.example.orders.api;

import com.example.orders.security.AppUser;
import com.example.orders.security.CurrentUser;
import com.example.orders.service.NewOrderItem;
import com.example.orders.service.OrderService;
import com.example.orders.service.OrderView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * HTTP mapping only. Identity comes from the authenticated session, rules live in {@link OrderService}.
 * Roles are declared per method with {@code @PreAuthorize}. Unannotated methods need any logged-in user.
 */
@RestController
@RequestMapping("/api/orders")
@Tag(name = "Orders")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("isAuthenticated()")
public class OrderController {

    private final OrderService service;

    public OrderController(OrderService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN')")    @Operation(summary = "Create an order for the logged-in user (CUSTOMER, ADMIN)")
    @ApiResponse(responseCode = "201", description = "Order created, Location header points to it")
    @ApiResponse(responseCode = "400", description = "Invalid request body")
    @ApiResponse(responseCode = "403", description = "Role may not create orders")
    public ResponseEntity<OrderResponse> create(
            @AuthenticationPrincipal AppUser user, @Valid @RequestBody CreateOrderRequest request) {
        var items = request.items().stream()
                .map(i -> new NewOrderItem(i.productId(), i.productName(), i.quantity(), i.unitPrice()))
                .toList();
        OrderView created = service.create(CurrentUser.from(user), items);
        URI location =
                ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").build(created.id());
        return ResponseEntity.created(location).body(OrderResponse.from(created));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPPORT', 'ADMIN')")    @Operation(summary = "List all orders, paged (SUPPORT, ADMIN)")
    @ApiResponse(responseCode = "403", description = "Role may not list all orders")
    public PageResponse<OrderResponse> listAll(
            @AuthenticationPrincipal AppUser user, @ParameterObject Pageable pageable) {
        return PageResponse.from(
                service.listAll(CurrentUser.from(user), PageableSupport.apply(pageable))
                        .map(OrderResponse::from));
    }

    @GetMapping("/my")
    @Operation(summary = "List the logged-in user's own orders, paged")
    public PageResponse<OrderResponse> listMine(
            @AuthenticationPrincipal AppUser user, @ParameterObject Pageable pageable) {
        return PageResponse.from(
                service.listMine(CurrentUser.from(user), PageableSupport.apply(pageable))
                        .map(OrderResponse::from));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get one order. Customers can only see their own")
    @ApiResponse(responseCode = "404", description = "No such order, or it belongs to someone else")
    public OrderResponse get(@AuthenticationPrincipal AppUser user, @PathVariable UUID id) {
        return OrderResponse.from(service.get(CurrentUser.from(user), id));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('SUPPORT', 'ADMIN')")    @Operation(summary = "Move an order to its next status (SUPPORT, ADMIN)")
    @ApiResponse(responseCode = "403", description = "Role may not change status")
    @ApiResponse(responseCode = "404", description = "No such order")
    @ApiResponse(responseCode = "409", description = "Not the next status, or the order changed concurrently")
    public OrderResponse updateStatus(
            @AuthenticationPrincipal AppUser user,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateStatusRequest request) {
        return OrderResponse.from(service.updateStatus(CurrentUser.from(user), id, request.status()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")    @Operation(summary = "Permanently delete an order (ADMIN)")
    @ApiResponse(responseCode = "204", description = "Order deleted")
    @ApiResponse(responseCode = "403", description = "Role may not delete orders")
    @ApiResponse(responseCode = "404", description = "No such order")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AppUser user, @PathVariable UUID id) {
        service.delete(CurrentUser.from(user), id);
        return ResponseEntity.noContent().build();
    }
}

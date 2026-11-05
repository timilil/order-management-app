package com.example.orders.service;

import com.example.orders.domain.Order;
import com.example.orders.domain.OrderItem;
import com.example.orders.domain.OrderStatus;
import com.example.orders.domain.Role;
import com.example.orders.error.OrderNotFoundException;
import com.example.orders.repository.OrderRepository;
import com.example.orders.security.CurrentUser;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Business rules for orders. Every public method is one transaction. Role rules and ownership checks
 * live here, so they hold no matter which controller or job calls the service.
 */
@Service
public class OrderService {

    private final OrderRepository orders;
    private final Clock clock;

    public OrderService(OrderRepository orders, Clock clock) {
        this.orders = orders;
        this.clock = clock;
    }

    /** CUSTOMER and ADMIN may create orders. The order always belongs to the caller. */
    @Transactional
    public OrderView create(CurrentUser user, List<NewOrderItem> newItems) {
        if (user.role() == Role.SUPPORT) {
            throw new AccessDeniedException("Support users cannot create orders");
        }
        List<OrderItem> items = newItems.stream()
                .map(i -> new OrderItem(i.productId(), i.productName(), i.quantity(), i.unitPrice()))
                .toList();
        Order order = orders.save(Order.create(user.id(), items, now()));
        return OrderView.from(order);
    }

    /** Customers see only their own orders. Another customer's order looks exactly like a missing one. */
    @Transactional(readOnly = true)
    public OrderView get(CurrentUser user, UUID orderId) {
        return OrderView.from(findVisible(user, orderId));
    }

    @Transactional(readOnly = true)
    public Page<OrderView> listMine(CurrentUser user, Pageable pageable) {
        return orders.findByCustomerId(user.id(), pageable).map(OrderView::from);
    }

    @Transactional(readOnly = true)
    public Page<OrderView> listAll(CurrentUser user, Pageable pageable) {
        requireStaff(user, "Only support and admin users can list all orders");
        return orders.findAll(pageable).map(OrderView::from);
    }

    /** SUPPORT and ADMIN only. The lifecycle rule itself is enforced by the domain. */
    @Transactional
    public OrderView updateStatus(CurrentUser user, UUID orderId, OrderStatus target) {
        requireStaff(user, "Only support and admin users can change an order's status");
        Order order = findVisible(user, orderId);
        order.changeStatus(target, now());
        return OrderView.from(order);
    }

    /** ADMIN only. Hard delete, the items are removed with the order. */
    @Transactional
    public void delete(CurrentUser user, UUID orderId) {
        if (user.role() != Role.ADMIN) {
            throw new AccessDeniedException("Only admin users can delete orders");
        }
        orders.delete(findVisible(user, orderId));
    }

    private Order findVisible(CurrentUser user, UUID orderId) {
        Order order = orders.findById(orderId).orElseThrow(() -> new OrderNotFoundException(orderId));
        if (!user.role().isStaff() && !order.isOwnedBy(user.id())) {
            throw new OrderNotFoundException(orderId);
        }
        return order;
    }

    private static void requireStaff(CurrentUser user, String message) {
        if (!user.role().isStaff()) {
            throw new AccessDeniedException(message);
        }
    }

    /** PostgreSQL stores microseconds, so truncating keeps what we return equal to what is stored. */
    private Instant now() {
        return Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
    }
}

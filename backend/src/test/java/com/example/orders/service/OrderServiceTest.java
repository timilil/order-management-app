package com.example.orders.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.orders.AbstractIntegrationTest;
import com.example.orders.domain.InvalidStatusTransitionException;
import com.example.orders.domain.OrderStatus;
import com.example.orders.domain.Role;
import com.example.orders.error.OrderNotFoundException;
import com.example.orders.security.CurrentUser;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;

/** Business rules against a real database: ownership, roles, persistence and transitions. */
class OrderServiceTest extends AbstractIntegrationTest {

    private static final List<NewOrderItem> KEYBOARD_AND_MOUSE = List.of(
            new NewOrderItem("PROD-101", "Keyboard", 2, new BigDecimal("49.99")),
            new NewOrderItem("PROD-102", "Mouse", 1, new BigDecimal("19.99")));

    @Autowired
    OrderService service;

    private CurrentUser newUser(Role role) {
        return new CurrentUser(createUser(role), role);
    }

    private OrderView createOrderFor(CurrentUser customer) {
        return service.create(customer, KEYBOARD_AND_MOUSE);
    }

    // --- create ---

    @Test
    void createCalculatesTotalAssignsCallerAndPersists() {
        CurrentUser customer = newUser(Role.CUSTOMER);

        OrderView created = createOrderFor(customer);

        assertThat(created.customerId()).isEqualTo(customer.id());
        assertThat(created.status()).isEqualTo(OrderStatus.CREATED);
        assertThat(created.totalAmount()).isEqualByComparingTo("119.97");
        assertThat(created.createdAt()).isEqualTo(created.updatedAt());

        OrderView reloaded = service.get(customer, created.id());
        assertThat(reloaded).isEqualTo(created);
        assertThat(reloaded.items()).hasSize(2);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM order_items WHERE order_id = ?", Integer.class, created.id()))
                .isEqualTo(2);
    }

    @Test
    void adminCanCreateAnOrderOwnedByTheAdmin() {
        CurrentUser admin = newUser(Role.ADMIN);

        assertThat(createOrderFor(admin).customerId()).isEqualTo(admin.id());
    }

    @Test
    void supportCannotCreateOrders() {
        CurrentUser support = newUser(Role.SUPPORT);

        assertThatThrownBy(() -> createOrderFor(support)).isInstanceOf(AccessDeniedException.class);
    }

    // --- get ---

    @Test
    void customerCanGetOwnOrder() {
        CurrentUser customer = newUser(Role.CUSTOMER);
        OrderView order = createOrderFor(customer);

        assertThat(service.get(customer, order.id()).id()).isEqualTo(order.id());
    }

    @Test
    void customerCannotGetAnotherCustomersOrder() {
        OrderView order = createOrderFor(newUser(Role.CUSTOMER));
        CurrentUser other = newUser(Role.CUSTOMER);

        assertThatThrownBy(() -> service.get(other, order.id())).isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    void supportAndAdminCanGetAnyOrder() {
        OrderView order = createOrderFor(newUser(Role.CUSTOMER));

        assertThat(service.get(newUser(Role.SUPPORT), order.id()).id()).isEqualTo(order.id());
        assertThat(service.get(newUser(Role.ADMIN), order.id()).id()).isEqualTo(order.id());
    }

    @Test
    void missingOrderIsNotFound() {
        assertThatThrownBy(() -> service.get(newUser(Role.ADMIN), UUID.randomUUID()))
                .isInstanceOf(OrderNotFoundException.class);
    }

    // --- lists ---

    @Test
    void listMineReturnsOnlyTheCallersOrders() {
        CurrentUser customer = newUser(Role.CUSTOMER);
        OrderView mine1 = createOrderFor(customer);
        OrderView mine2 = createOrderFor(customer);
        createOrderFor(newUser(Role.CUSTOMER));

        Page<OrderView> page = service.listMine(customer, PageRequest.of(0, 10));

        assertThat(page.getContent()).extracting(OrderView::id).containsExactlyInAnyOrder(mine1.id(), mine2.id());
    }

    @Test
    void listMineIsPagedAndSorted() {
        CurrentUser customer = newUser(Role.CUSTOMER);
        service.create(customer, List.of(new NewOrderItem("P-1", "Cheap", 1, new BigDecimal("1.00"))));
        service.create(customer, List.of(new NewOrderItem("P-2", "Middle", 1, new BigDecimal("2.00"))));
        service.create(customer, List.of(new NewOrderItem("P-3", "Pricey", 1, new BigDecimal("3.00"))));

        Page<OrderView> first = service.listMine(customer, PageRequest.of(0, 2, Sort.by("totalAmount").descending()));
        Page<OrderView> second = service.listMine(customer, PageRequest.of(1, 2, Sort.by("totalAmount").descending()));

        assertThat(first.getTotalElements()).isEqualTo(3);
        assertThat(first.getTotalPages()).isEqualTo(2);
        assertThat(first.getContent()).extracting(OrderView::totalAmount)
                .usingComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .containsExactly(new BigDecimal("3.00"), new BigDecimal("2.00"));
        assertThat(second.getContent()).extracting(OrderView::totalAmount)
                .usingComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .containsExactly(new BigDecimal("1.00"));
    }

    @Test
    void customerCannotListAllOrders() {
        CurrentUser customer = newUser(Role.CUSTOMER);

        assertThatThrownBy(() -> service.listAll(customer, PageRequest.of(0, 10)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void supportAndAdminCanListAllOrders() {
        OrderView order = createOrderFor(newUser(Role.CUSTOMER));
        var everything = PageRequest.of(0, 1000);

        assertThat(service.listAll(newUser(Role.SUPPORT), everything).getContent())
                .extracting(OrderView::id)
                .contains(order.id());
        assertThat(service.listAll(newUser(Role.ADMIN), everything).getContent())
                .extracting(OrderView::id)
                .contains(order.id());
    }

    // --- status ---

    @Test
    void supportCanMoveOrderThroughTheWholeLifecycle() {
        OrderView order = createOrderFor(newUser(Role.CUSTOMER));
        CurrentUser support = newUser(Role.SUPPORT);

        OrderView confirmed = service.updateStatus(support, order.id(), OrderStatus.CONFIRMED);
        OrderView shipped = service.updateStatus(support, order.id(), OrderStatus.SHIPPED);
        OrderView delivered = service.updateStatus(support, order.id(), OrderStatus.DELIVERED);

        assertThat(confirmed.status()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(shipped.status()).isEqualTo(OrderStatus.SHIPPED);
        assertThat(delivered.status()).isEqualTo(OrderStatus.DELIVERED);
        assertThat(delivered.updatedAt()).isAfter(order.createdAt());
        assertThat(delivered.createdAt()).isEqualTo(order.createdAt());
        assertThat(service.get(support, order.id()).status()).isEqualTo(OrderStatus.DELIVERED);
    }

    @Test
    void adminCanChangeStatus() {
        OrderView order = createOrderFor(newUser(Role.CUSTOMER));

        OrderView confirmed = service.updateStatus(newUser(Role.ADMIN), order.id(), OrderStatus.CONFIRMED);

        assertThat(confirmed.status()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    void customerCannotChangeStatusEvenOfOwnOrder() {
        CurrentUser customer = newUser(Role.CUSTOMER);
        OrderView order = createOrderFor(customer);

        assertThatThrownBy(() -> service.updateStatus(customer, order.id(), OrderStatus.CONFIRMED))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(service.get(customer, order.id()).status()).isEqualTo(OrderStatus.CREATED);
    }

    @Test
    void invalidTransitionIsRejectedAndNothingIsChanged() {
        CurrentUser customer = newUser(Role.CUSTOMER);
        OrderView order = createOrderFor(customer);
        CurrentUser support = newUser(Role.SUPPORT);

        assertThatThrownBy(() -> service.updateStatus(support, order.id(), OrderStatus.SHIPPED))
                .isInstanceOf(InvalidStatusTransitionException.class);
        assertThat(service.get(customer, order.id()).status()).isEqualTo(OrderStatus.CREATED);
    }

    @Test
    void statusUpdateOfMissingOrderIsNotFound() {
        assertThatThrownBy(() -> service.updateStatus(newUser(Role.SUPPORT), UUID.randomUUID(), OrderStatus.CONFIRMED))
                .isInstanceOf(OrderNotFoundException.class);
    }

    // --- delete ---

    @Test
    void adminCanDeleteAnOrderAndItsItems() {
        OrderView order = createOrderFor(newUser(Role.CUSTOMER));
        CurrentUser admin = newUser(Role.ADMIN);

        service.delete(admin, order.id());

        assertThatThrownBy(() -> service.get(admin, order.id())).isInstanceOf(OrderNotFoundException.class);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM order_items WHERE order_id = ?", Integer.class, order.id()))
                .isZero();
    }

    @Test
    void supportAndCustomerCannotDelete() {
        CurrentUser customer = newUser(Role.CUSTOMER);
        OrderView order = createOrderFor(customer);

        assertThatThrownBy(() -> service.delete(newUser(Role.SUPPORT), order.id()))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.delete(customer, order.id())).isInstanceOf(AccessDeniedException.class);
        assertThat(service.get(customer, order.id())).isNotNull();
    }
}

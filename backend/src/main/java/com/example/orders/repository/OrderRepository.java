package com.example.orders.repository;

import com.example.orders.domain.Order;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    /** Uses the index on {@code customer_id}. */
    Page<Order> findByCustomerId(UUID customerId, Pageable pageable);
}

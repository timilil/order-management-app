package com.example.orders.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

class OrderStatusTest {

    @Test
    void lifecycleMovesForwardOneStepAtATime() {
        assertThat(OrderStatus.CREATED.next()).contains(OrderStatus.CONFIRMED);
        assertThat(OrderStatus.CONFIRMED.next()).contains(OrderStatus.SHIPPED);
        assertThat(OrderStatus.SHIPPED.next()).contains(OrderStatus.DELIVERED);
    }

    @Test
    void deliveredIsFinal() {
        assertThat(OrderStatus.DELIVERED.next()).isEqualTo(Optional.empty());
    }

    @ParameterizedTest(name = "{0} -> {1} is allowed")
    @MethodSource("validTransitions")
    void allowsForwardTransitions(OrderStatus from, OrderStatus to) {
        assertThat(from.canTransitionTo(to)).isTrue();
    }

    @ParameterizedTest(name = "{0} -> {1} is rejected")
    @MethodSource("invalidTransitions")
    void rejectsEverythingElse(OrderStatus from, OrderStatus to) {
        assertThat(from.canTransitionTo(to)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(OrderStatus.class)
    void statusCannotTransitionToItself(OrderStatus status) {
        assertThat(status.canTransitionTo(status)).isFalse();
    }

    /** The expected lifecycle, written out independently of the implementation. */
    private static final Map<OrderStatus, OrderStatus> EXPECTED_NEXT = Map.of(
            OrderStatus.CREATED, OrderStatus.CONFIRMED,
            OrderStatus.CONFIRMED, OrderStatus.SHIPPED,
            OrderStatus.SHIPPED, OrderStatus.DELIVERED);

    static Stream<Arguments> validTransitions() {
        return EXPECTED_NEXT.entrySet().stream().map(e -> Arguments.of(e.getKey(), e.getValue()));
    }

    /** All 16 combinations minus the 3 valid ones. Includes skipping, going backwards and staying put. */
    static Stream<Arguments> invalidTransitions() {
        return Arrays.stream(OrderStatus.values())
                .flatMap(from -> Arrays.stream(OrderStatus.values())
                        .filter(to -> EXPECTED_NEXT.get(from) != to)
                        .map(to -> Arguments.of(from, to)));
    }
}

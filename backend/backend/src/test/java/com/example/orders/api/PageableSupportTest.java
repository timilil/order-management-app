package com.example.orders.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.orders.error.InvalidRequestException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

class PageableSupportTest {

    @Test
    void defaultsToNewestFirst() {
        Pageable result = PageableSupport.apply(PageRequest.of(2, 20));

        assertThat(result.getPageNumber()).isEqualTo(2);
        assertThat(result.getPageSize()).isEqualTo(20);
        assertThat(result.getSort().getOrderFor("createdAt").getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @ParameterizedTest
    @ValueSource(strings = {"createdAt", "updatedAt", "status", "totalAmount"})
    void acceptsAllowedSortFields(String field) {
        Pageable result = PageableSupport.apply(PageRequest.of(0, 10, Sort.by(Sort.Direction.ASC, field)));

        assertThat(result.getSort().getOrderFor(field).getDirection()).isEqualTo(Sort.Direction.ASC);
    }

    @Test
    void addsIdAsFinalTieBreaker() {
        Pageable result = PageableSupport.apply(PageRequest.of(0, 10, Sort.by("status")));

        assertThat(result.getSort().stream().map(Sort.Order::getProperty)).containsExactly("status", "id");
    }

    @ParameterizedTest
    @ValueSource(strings = {"id", "customerId", "version", "password", "items.unitPrice"})
    void rejectsOtherSortFields(String field) {
        assertThatThrownBy(() -> PageableSupport.apply(PageRequest.of(0, 10, Sort.by(field))))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("Allowed fields");
    }
}

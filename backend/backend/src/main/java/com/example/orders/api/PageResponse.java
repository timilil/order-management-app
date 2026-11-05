package com.example.orders.api;

import java.util.List;
import org.springframework.data.domain.Page;

/** Stable JSON shape for a page of results. {@code page} starts at 0. */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
}

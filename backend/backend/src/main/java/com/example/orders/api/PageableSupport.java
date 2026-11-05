package com.example.orders.api;

import com.example.orders.error.InvalidRequestException;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Applies the paging rules to the {@code page}, {@code size} and {@code sort} request parameters.
 * Spring Data already caps the size (see {@code spring.data.web.pageable}). This adds the default
 * sort and the list of fields clients may sort by.
 */
final class PageableSupport {

    static final Set<String> SORTABLE_FIELDS = Set.of("createdAt", "updatedAt", "status", "totalAmount");

    private PageableSupport() {
    }

    /**
     * @throws InvalidRequestException if the sort field is not allowed
     */
    static Pageable apply(Pageable requested) {
        Sort sort = requested.getSort();
        if (sort.isUnsorted()) {
            sort = Sort.by(Sort.Direction.DESC, "createdAt");
        } else {
            for (Sort.Order order : sort) {
                if (!SORTABLE_FIELDS.contains(order.getProperty())) {
                    throw new InvalidRequestException(
                            "Cannot sort by '" + order.getProperty() + "'. Allowed fields: "
                                    + String.join(", ", SORTABLE_FIELDS.stream().sorted().toList()));
                }
            }
        }
        // The ID as last sort key keeps page boundaries stable when other values are equal.
        return PageRequest.of(requested.getPageNumber(), requested.getPageSize(), sort.and(Sort.by("id")));
    }
}

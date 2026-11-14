package com.acme.operations.common;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;

import java.util.Set;

// Shared utility to validate pagination and build the object of Spring Data.
public final class PageRequests {
    // This class groups related types or utilities and does not need an instance.
    private PageRequests() {}

    // Convert API parameters into a valid page request.
    public static PageRequest of(
            int page, int size, String sort, String direction, Set<String> allowed) {
        // Valid range, size, allowed column and direction before building the order.
        if (page < 0
                || page > 1_000_000
                || size < 1
                || size > 100
                || !allowed.contains(sort)
                || !(direction.equalsIgnoreCase("asc") || direction.equalsIgnoreCase("desc"))) {
            // An invalid option is reported as 400 INVALID_PAGE.
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_PAGE",
                    "Invalid pagination or sort; size must be 1..100");
        }
        // Create the order after validating both column and address.
        Sort ordering = Sort.by(Sort.Direction.fromString(direction), sort);
        // Add how to unload so that the pages have a stable order.
        if (!sort.equals("id")) ordering = ordering.and(Sort.by("id"));
        // PageRequest uses indices from scratch: page = 2 represents the third page.
        return PageRequest.of(page, size, ordering);
    }
}

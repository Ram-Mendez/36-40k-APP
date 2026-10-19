package com.acme.operations.common;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;

import java.util.Set;

public final class PageRequests {
    private PageRequests() {}

    public static PageRequest of(
            int page, int size, String sort, String direction, Set<String> allowed) {
        if (page < 0
                || page > 1_000_000
                || size < 1
                || size > 100
                || !allowed.contains(sort)
                || !(direction.equalsIgnoreCase("asc") || direction.equalsIgnoreCase("desc"))) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_PAGE",
                    "Invalid pagination or sort; size must be 1..100");
        }
        Sort ordering = Sort.by(Sort.Direction.fromString(direction), sort);
        if (!sort.equals("id")) ordering = ordering.and(Sort.by("id"));
        return PageRequest.of(page, size, ordering);
    }
}

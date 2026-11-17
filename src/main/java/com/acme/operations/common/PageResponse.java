package com.acme.operations.common;

import org.springframework.data.domain.Page;

import java.util.List;

// Generic Container: T is the type of each element, accompanied by pagination metadata.
public record PageResponse<T>(
        List<T> content, int page, int size, long totalElements, int totalPages) {
    // Adapts a Spring Data Page to the common JSON format of this API.
    public static <T> PageResponse<T> from(Page<T> result) {
        // Copy content, page index, size and totals calculated by the query.
        return new PageResponse<>(
                result.getContent(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }
}

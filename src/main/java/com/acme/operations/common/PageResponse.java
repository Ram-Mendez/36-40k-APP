package com.acme.operations.common;

import org.springframework.data.domain.Page;

import java.util.List;

// Contenedor genérico: T es el tipo de cada elemento, acompañado de metadatos de paginación.
public record PageResponse<T>(
        List<T> content, int page, int size, long totalElements, int totalPages) {
    // Adapta una Page de Spring Data al formato JSON común de esta API.
    public static <T> PageResponse<T> from(Page<T> result) {
        // Copia contenido, índice de página, tamaño y totales calculados por la consulta.
        return new PageResponse<>(
                result.getContent(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }
}

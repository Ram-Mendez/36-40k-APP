package com.acme.operations.common;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;

import java.util.Set;

// Utilidad compartida para validar paginación y construir el objeto de Spring Data.
public final class PageRequests {
    // Constructor privado: se usa el método estático of, no se crean instancias.
    private PageRequests() {}

    // Convierte parámetros de la API en una solicitud de página válida.
    public static PageRequest of(
            int page, int size, String sort, String direction, Set<String> allowed) {
        // Valida rango, tamaño, columna permitida y dirección antes de construir el orden.
        if (page < 0
                || page > 1_000_000
                || size < 1
                || size > 100
                || !allowed.contains(sort)
                || !(direction.equalsIgnoreCase("asc") || direction.equalsIgnoreCase("desc"))) {
            // Una opción inválida se comunica como 400 INVALID_PAGE.
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_PAGE",
                    "Invalid pagination or sort; size must be 1..100");
        }
        // Crea el orden pedido después de validar tanto columna como dirección.
        Sort ordering = Sort.by(Sort.Direction.fromString(direction), sort);
        // Añade id como desempate para que las páginas tengan un orden estable.
        if (!sort.equals("id")) ordering = ordering.and(Sort.by("id"));
        // PageRequest usa índices desde cero: page=2 representa la tercera página.
        return PageRequest.of(page, size, ordering);
    }
}

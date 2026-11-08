package com.acme.operations.report;

import com.acme.operations.common.BusinessException;
import com.acme.operations.common.PageResponse;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

// Spring registra esta clase como servicio y permite inyectarla; las reglas las implementan sus métodos.
@Service
// En las lecturas, REPEATABLE_READ mantiene coherentes el conteo y las filas del informe.
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class ReportService {
    // Acceso a persistencia separado de las reglas que coordina este servicio.
    private final ReportRepository repository;

    // Spring suministra las dependencias a este único constructor; no hace falta escribir @Autowired.
    public ReportService(ReportRepository repository) {
        // Conserva la referencia recibida para que los métodos puedan reutilizar esta dependencia.
        this.repository = repository;
    }

    // Valida el intervalo antes de consultar ventas completadas por cliente.
    public PageResponse<ReportDtos.CustomerSales> sales(
            Instant from, Instant to, int page, int size) {
        // Si llegan ambas fechas, from debe ser estrictamente anterior a to.
        if (from != null && to != null && !from.isBefore(to))
            // Rechaza con 400 un intervalo vacío o invertido.
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST, "INVALID_DATE_RANGE", "from must be earlier than to");
        // Delega el cálculo SQL y devuelve su página de resultados.
        return repository.sales(from, to, page, size);
    }

    // Lista productos cuyo disponible está por debajo o igual al umbral.
    public PageResponse<ReportDtos.LowStock> lowStock(long threshold, int page, int size) {
        // El repositorio calcula físico menos reservado y aplica la paginación.
        return repository.lowStock(threshold, page, size);
    }
}

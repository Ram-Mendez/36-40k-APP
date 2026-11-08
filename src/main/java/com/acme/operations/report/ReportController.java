package com.acme.operations.report;

import com.acme.operations.common.PageResponse;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

// Spring atiende las rutas de esta clase y serializa los valores devueltos como JSON.
@RestController
// Prefijo común de las rutas; se combina con el mapping de cada método.
@RequestMapping("/api/v1/reports")
// Activa validación de restricciones en los parámetros de estos métodos.
@Validated
// Spring Security permite consultas a ADMIN, OPERATIONS y READONLY.
@PreAuthorize("hasAnyRole('ADMIN','OPERATIONS','READONLY')")
public class ReportController {
    // Servicio al que el controlador delega las reglas y operaciones de negocio.
    private final ReportService service;

    // Spring suministra las dependencias a este único constructor; no hace falta escribir @Autowired.
    public ReportController(ReportService service) {
        // Conserva la referencia recibida para que los métodos puedan reutilizar esta dependencia.
        this.service = service;
    }

    // Expone una consulta HTTP GET; Spring selecciona este método para la ruta indicada.
    @GetMapping("/sales")
    // Consulta ventas completadas por cliente dentro del intervalo indicado.
    public PageResponse<ReportDtos.CustomerSales> sales(
            // Lee un filtro opcional de la URL; si se omite, llega null.
            @RequestParam(required = false) Instant from,
            // Lee un filtro opcional de la URL; si se omite, llega null.
            @RequestParam(required = false) Instant to,
            // Lee page de la URL: 0 es la primera página; usa el valor por defecto si se omite.
            @RequestParam(defaultValue = "0") @Min(0) int page,
            // Lee cuántos resultados se piden por página; las restricciones indicadas limitan ese valor.
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        // Consulta ventas completadas por cliente dentro del intervalo indicado.
        return service.sales(from, to, page, size);
    }

    // Expone una consulta HTTP GET; Spring selecciona este método para la ruta indicada.
    @GetMapping("/low-stock")
    // Consulta productos cuyo disponible cumple el umbral indicado.
    public PageResponse<ReportDtos.LowStock> lowStock(
            // Lee este parámetro de la URL y usa el valor indicado cuando falta.
            @RequestParam(defaultValue = "10") @Min(0) long threshold,
            // Lee page de la URL: 0 es la primera página; usa el valor por defecto si se omite.
            @RequestParam(defaultValue = "0") @Min(0) int page,
            // Lee cuántos resultados se piden por página; las restricciones indicadas limitan ese valor.
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        // Consulta productos cuyo disponible cumple el umbral indicado.
        return service.lowStock(threshold, page, size);
    }
}

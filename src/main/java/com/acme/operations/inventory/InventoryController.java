package com.acme.operations.inventory;

import com.acme.operations.common.PageResponse;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

// Spring atiende las rutas de esta clase y serializa los valores devueltos como JSON.
@RestController
// Prefijo común de las rutas; se combina con el mapping de cada método.
@RequestMapping("/api/v1/inventory")
// Activa validación de restricciones en los parámetros de estos métodos.
@Validated
// Spring Security permite consultas a ADMIN, OPERATIONS y READONLY.
@PreAuthorize("hasAnyRole('ADMIN','OPERATIONS','READONLY')")
public class InventoryController {
    // Servicio al que el controlador delega las reglas y operaciones de negocio.
    private final InventoryService service;

    // Spring suministra las dependencias a este único constructor; no hace falta escribir @Autowired.
    public InventoryController(InventoryService service) {
        // Conserva la referencia recibida para que los métodos puedan reutilizar esta dependencia.
        this.service = service;
    }

    // Expone una consulta HTTP GET; Spring selecciona este método para la ruta indicada.
    @GetMapping
    // Devuelve saldos actuales por producto: físico, reservado y disponible.
    public PageResponse<InventoryDtos.Balance> balances(
            // Lee un filtro opcional de la URL; si se omite, llega null.
            @RequestParam(required = false) Long productId,
            // Lee page de la URL: 0 es la primera página; usa el valor por defecto si se omite.
            @RequestParam(defaultValue = "0") @Min(0) int page,
            // Lee cuántos resultados se piden por página; las restricciones indicadas limitan ese valor.
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        // Devuelve saldos actuales por producto: físico, reservado y disponible.
        return service.balances(productId, page, size);
    }

    // Expone una acción HTTP POST; el servicio determina los cambios que se realizan.
    @PostMapping("/{productId}/adjustments")
    // Si termina correctamente, Spring responde 204 sin cuerpo; las excepciones siguen su propio manejo.
    @ResponseStatus(HttpStatus.NO_CONTENT)
    // Spring Security permite esta operación solo a ADMIN u OPERATIONS.
    @PreAuthorize("hasAnyRole('ADMIN','OPERATIONS')")
    // Delega el ajuste físico junto con su movimiento y auditoría transaccionales.
    public void adjust(
            // Spring toma el identificador de la ruta; @RequestBody, si aparece, se obtiene del JSON.
            @PathVariable long productId, @Valid @RequestBody InventoryDtos.Adjustment request) {
        // Delega el ajuste físico junto con su movimiento y auditoría transaccionales.
        service.adjust(productId, request);
    }

    // Expone una consulta HTTP GET; Spring selecciona este método para la ruta indicada.
    // PARECE QUE pinta aqui
    @GetMapping("/movements")
    // Devuelve el historial de movimientos; no calcula una reserva nueva.
    public PageResponse<InventoryDtos.Movement> movements(
            // Lee un filtro opcional de la URL; si se omite, llega null.
            @RequestParam(required = false) Long productId,
            // Lee page de la URL: 0 es la primera página; usa el valor por defecto si se omite.
            @RequestParam(defaultValue = "0") @Min(0) int page,
            // Lee cuántos resultados se piden por página; las restricciones indicadas limitan ese valor.
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        // Devuelve el historial de movimientos; no calcula una reserva nueva.
        return service.movements(productId, page, size);
    }

    // Expone una consulta HTTP GET; Spring selecciona este método para la ruta indicada.
    @GetMapping("/reservations")
    // Lista las reservas existentes por pedido y producto; este GET no reserva unidades.
    public PageResponse<InventoryDtos.Reservation> reservations(
            // Lee page de la URL: 0 es la primera página; usa el valor por defecto si se omite.
            @RequestParam(defaultValue = "0") @Min(0) int page,
            // Lee cuántos resultados se piden por página; las restricciones indicadas limitan ese valor.
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        // Lista las reservas existentes por pedido y producto; este GET no reserva unidades.
        return service.reservations(page, size);
    }
}

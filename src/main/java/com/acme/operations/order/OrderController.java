package com.acme.operations.order;

import com.acme.operations.common.PageResponse;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

// Spring atiende las rutas de esta clase y serializa los valores devueltos como JSON.
@RestController
// Prefijo común de las rutas; se combina con el mapping de cada método.
@RequestMapping("/api/v1/orders")
// Activa validación de restricciones en los parámetros de estos métodos.
@Validated
// Spring Security permite esta operación solo a ADMIN u OPERATIONS.
@PreAuthorize("hasAnyRole('ADMIN','OPERATIONS')")
public class OrderController {
    // Servicio al que el controlador delega las reglas y operaciones de negocio.
    private final OrderService service;
    // Coordina la llamada HTTP a shipping y después la finalización transaccional.
    private final OrderCompletionService completion;

    // Spring suministra las dependencias a este único constructor; no hace falta escribir @Autowired.
    public OrderController(OrderService service, OrderCompletionService completion) {
        // Conserva la referencia recibida para que los métodos puedan reutilizar esta dependencia.
        this.service = service;
        this.completion = completion;
    }

    // Expone una consulta HTTP GET; Spring selecciona este método para la ruta indicada.
    @GetMapping
    // Spring Security permite consultas a ADMIN, OPERATIONS y READONLY.
    @PreAuthorize("hasAnyRole('ADMIN','OPERATIONS','READONLY')")
    // Busca pedidos con filtros y orden; devuelve contenido y metadatos de página.
    public PageResponse<OrderDtos.Order> search(
            // Lee un filtro opcional de la URL; si se omite, llega null.
            @RequestParam(required = false) Long customerId,
            // Lee un filtro opcional de la URL; si se omite, llega null.
            @RequestParam(required = false) String status,
            // Lee page de la URL: 0 es la primera página; usa el valor por defecto si se omite.
            @RequestParam(defaultValue = "0") @Min(0) int page,
            // Lee cuántos resultados se piden por página; las restricciones indicadas limitan ese valor.
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            // Lee este parámetro de la URL y usa el valor indicado cuando falta.
            @RequestParam(defaultValue = "createdAt,desc") String sort) {
        // Busca pedidos con filtros y orden; devuelve contenido y metadatos de página.
        return service.search(customerId, status, page, size, sort);
    }

    // Expone una consulta HTTP GET; Spring selecciona este método para la ruta indicada.
    @GetMapping("/{id}")
    // Spring Security permite consultas a ADMIN, OPERATIONS y READONLY.
    @PreAuthorize("hasAnyRole('ADMIN','OPERATIONS','READONLY')")
    // Recupera el recurso por id; si falta, el servicio interrumpe el flujo con un error. El id lo toma Spring de la
    // ruta.
    public OrderDtos.Order get(@PathVariable long id) {
        // Recupera el recurso por id; si falta, el servicio interrumpe el flujo con un error.
        return service.get(id);
    }

    // Expone una acción HTTP POST; el servicio determina los cambios que se realizan.
    @PostMapping
    // Delega el alta y recibe el objeto creado con sus datos generados. Spring convierte y valida el JSON antes de
    // entrar.
    public ResponseEntity<OrderDtos.Order> create(@Valid @RequestBody OrderDtos.Create request) {
        // Delega el alta y recibe el objeto creado con sus datos generados.
        var result = service.create(request);
        // Devuelve 201, la URL del recurso en Location y su DTO como cuerpo JSON.
        return ResponseEntity.created(URI.create("/api/v1/orders/" + result.id())).body(result);
    }

    // Expone una actualización HTTP PUT de la ruta indicada.
    @PutMapping("/{id}")
    // Delega la edición; el servicio valida antes de persistir los nuevos datos.
    public OrderDtos.Order update(
            // Spring toma el identificador de la ruta; @RequestBody, si aparece, se obtiene del JSON.
            @PathVariable long id, @Valid @RequestBody OrderDtos.Create request) {
        // Delega la edición; el servicio valida antes de persistir los nuevos datos.
        return service.update(id, request);
    }

    // Expone una actualización HTTP PUT de la ruta indicada.
    @PutMapping("/{id}/lines/{productId}")
    // Fija la cantidad de un producto en el pedido; las reglas se comprueban en el servicio.
    public OrderDtos.Order setLine(
            // Spring toma el identificador de la ruta; @RequestBody, si aparece, se obtiene del JSON.
            @PathVariable long id,
            // Spring toma el identificador de la ruta; @RequestBody, si aparece, se obtiene del JSON.
            @PathVariable long productId,
            // Spring convierte el JSON al DTO y valida sus restricciones antes de ejecutar el método.
            @Valid @RequestBody OrderDtos.SetLine request) {
        // Fija la cantidad de un producto en el pedido; las reglas se comprueban en el servicio.
        return service.setLine(id, productId, request);
    }

    // Expone HTTP DELETE; el cuerpo del método determina si elimina una línea o desactiva un recurso.
    @DeleteMapping("/{id}/lines/{productId}")
    // Retira ese producto del pedido y recalcula su total en el servicio. El id lo toma Spring de la ruta.
    public OrderDtos.Order removeLine(@PathVariable long id, @PathVariable long productId) {
        // Retira ese producto del pedido y recalcula su total en el servicio.
        return service.removeLine(id, productId);
    }

    // Expone una acción HTTP POST; el servicio determina los cambios que se realizan.
    @PostMapping("/{id}/confirm")
    // Confirma el pedido y reserva sus productos dentro de la transacción del servicio. El id lo toma Spring de la
    // ruta.
    public OrderDtos.Order confirm(@PathVariable long id) {
        // Confirma el pedido y reserva sus productos dentro de la transacción del servicio.
        return service.confirm(id);
    }

    // Expone una acción HTTP POST; el servicio determina los cambios que se realizan.
    @PostMapping("/{id}/cancel")
    // Cancela el pedido y libera reservas cuando corresponde. El id lo toma Spring de la ruta.
    public OrderDtos.Order cancel(@PathVariable long id) {
        // Cancela el pedido y libera reservas cuando corresponde.
        return service.cancel(id);
    }

    // Expone una acción HTTP POST; el servicio determina los cambios que se realizan.
    @PostMapping("/{id}/complete")
    // Primero obtiene shipping y luego completa el pedido con sus controles de estado y versión. El id lo toma Spring
    // de la ruta.
    public OrderDtos.Order complete(@PathVariable long id) {
        // Primero obtiene shipping y luego completa el pedido con sus controles de estado y versión.
        return completion.complete(id);
    }
}

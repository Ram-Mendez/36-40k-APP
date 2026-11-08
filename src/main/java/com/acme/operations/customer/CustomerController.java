package com.acme.operations.customer;

import com.acme.operations.common.PageResponse;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

// Spring atiende las rutas de esta clase y serializa los valores devueltos como JSON.
@RestController
// Prefijo común de las rutas; se combina con el mapping de cada método.
@RequestMapping("/api/v1/customers")
// Spring Security permite consultas a ADMIN, OPERATIONS y READONLY.
@PreAuthorize("hasAnyRole('ADMIN','OPERATIONS','READONLY')")
public class CustomerController {
    // Servicio al que el controlador delega las reglas y operaciones de negocio.
    private final CustomerService service;

    // Spring suministra las dependencias a este único constructor; no hace falta escribir @Autowired.
    public CustomerController(CustomerService service) {
        // Conserva la referencia recibida para que los métodos puedan reutilizar esta dependencia.
        this.service = service;
    }

    // Expone una consulta HTTP GET; Spring selecciona este método para la ruta indicada.
    @GetMapping
    // Consulta una página con los filtros recibidos; no crea ni modifica registros.
    public PageResponse<CustomerDtos.View> list(
            // Lee page de la URL: 0 es la primera página; usa el valor por defecto si se omite.
            @RequestParam(defaultValue = "0") int page,
            // Lee cuántos resultados se piden por página; las restricciones indicadas limitan ese valor.
            @RequestParam(defaultValue = "20") int size,
            // Lee este parámetro de la URL y usa el valor indicado cuando falta.
            @RequestParam(defaultValue = "name") String sort,
            // Lee este parámetro de la URL y usa el valor indicado cuando falta.
            @RequestParam(defaultValue = "asc") String direction,
            // Lee un filtro opcional de la URL; si se omite, llega null.
            @RequestParam(required = false) String search,
            // Lee un filtro opcional de la URL; si se omite, llega null.
            @RequestParam(required = false) Boolean active) {
        // Consulta una página con los filtros recibidos; no crea ni modifica registros.
        return service.list(page, size, sort, direction, search, active);
    }

    // Expone una consulta HTTP GET; Spring selecciona este método para la ruta indicada.
    @GetMapping("/{id}")
    // Recupera el recurso por id; si falta, el servicio interrumpe el flujo con un error. El id lo toma Spring de la
    // ruta.
    public CustomerDtos.View get(@PathVariable long id) {
        // Recupera el recurso por id; si falta, el servicio interrumpe el flujo con un error.
        return service.get(id);
    }

    // Expone una acción HTTP POST; el servicio determina los cambios que se realizan.
    @PostMapping
    // Spring Security permite esta operación solo a ADMIN u OPERATIONS.
    @PreAuthorize("hasAnyRole('ADMIN','OPERATIONS')")
    // Delega el alta y recibe el objeto creado con sus datos generados. Spring convierte y valida el JSON antes de
    // entrar.
    public ResponseEntity<CustomerDtos.View> create(@Valid @RequestBody CustomerDtos.Input input) {
        // Delega el alta y recibe el objeto creado con sus datos generados.
        var result = service.create(input);
        // Devuelve 201, la URL del recurso en Location y su DTO como cuerpo JSON.
        return ResponseEntity.created(URI.create("/api/v1/customers/" + result.id())).body(result);
    }

    // Expone una actualización HTTP PUT de la ruta indicada.
    @PutMapping("/{id}")
    // Spring Security permite esta operación solo a ADMIN u OPERATIONS.
    @PreAuthorize("hasAnyRole('ADMIN','OPERATIONS')")
    // Delega la edición; el servicio valida antes de persistir los nuevos datos.
    public CustomerDtos.View update(
            // Spring toma el identificador de la ruta; @RequestBody, si aparece, se obtiene del JSON.
            @PathVariable long id, @Valid @RequestBody CustomerDtos.Input input) {
        // Delega la edición; el servicio valida antes de persistir los nuevos datos.
        return service.update(id, input);
    }

    // Expone HTTP DELETE; el cuerpo del método determina si elimina una línea o desactiva un recurso.
    @DeleteMapping("/{id}")
    // Spring Security permite esta operación solo a ADMIN u OPERATIONS.
    @PreAuthorize("hasAnyRole('ADMIN','OPERATIONS')")
    // Solicita una baja lógica: desactiva el recurso y conserva su historial. El id lo toma Spring de la ruta.
    public ResponseEntity<Void> deactivate(@PathVariable long id) {
        // Solicita una baja lógica: desactiva el recurso y conserva su historial.
        service.deactivate(id);
        // Devuelve 204 sin JSON porque la operación ya terminó y no necesita cuerpo de respuesta.
        return ResponseEntity.noContent().build();
    }
}

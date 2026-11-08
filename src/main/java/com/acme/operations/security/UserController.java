package com.acme.operations.security;

import com.acme.operations.common.PageResponse;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

// Spring atiende las rutas de esta clase y serializa los valores devueltos como JSON.
@RestController
// Prefijo común de las rutas; se combina con el mapping de cada método.
@RequestMapping("/api/v1/users")
public class UserController {
    // Servicio al que el controlador delega las reglas y operaciones de negocio.
    private final UserService service;

    // Spring suministra las dependencias a este único constructor; no hace falta escribir @Autowired.
    public UserController(UserService service) {
        // Conserva la referencia recibida para que los métodos puedan reutilizar esta dependencia.
        this.service = service;
    }

    // Expone una consulta HTTP GET; Spring selecciona este método para la ruta indicada.
    @GetMapping
    // Consulta una página con los filtros recibidos; no crea ni modifica registros.
    public PageResponse<UserDtos.View> list(
            // Lee page de la URL: 0 es la primera página; usa el valor por defecto si se omite.
            @RequestParam(defaultValue = "0") int page,
            // Lee cuántos resultados se piden por página; las restricciones indicadas limitan ese valor.
            @RequestParam(defaultValue = "20") int size,
            // Lee un filtro opcional de la URL; si se omite, llega null.
            @RequestParam(required = false) String query,
            // Lee un filtro opcional de la URL; si se omite, llega null.
            @RequestParam(required = false) Boolean active) {
        // Consulta una página con los filtros recibidos; no crea ni modifica registros.
        return service.list(page, size, query, active);
    }

    // Expone una acción HTTP POST; el servicio determina los cambios que se realizan.
    @PostMapping
    // Si termina correctamente, Spring responde 201 indicando que se creó el recurso.
    @ResponseStatus(HttpStatus.CREATED)
    // Delega el alta y recibe el objeto creado con sus datos generados. Spring convierte y valida el JSON antes de
    // entrar.
    public UserDtos.View create(@Valid @RequestBody UserDtos.Create request) {
        // Delega el alta y recibe el objeto creado con sus datos generados.
        return service.create(request);
    }

    // Expone una actualización HTTP PUT de la ruta indicada.
    @PutMapping("/{id}")
    // Delega la edición; el servicio valida antes de persistir los nuevos datos.
    public UserDtos.View update(
            // Spring toma el identificador de la ruta; @RequestBody, si aparece, se obtiene del JSON.
            @PathVariable long id, @Valid @RequestBody UserDtos.Update request) {
        // Delega la edición; el servicio valida antes de persistir los nuevos datos.
        return service.update(id, request);
    }

    // Expone HTTP DELETE; el cuerpo del método determina si elimina una línea o desactiva un recurso.
    @DeleteMapping("/{id}")
    // Si termina correctamente, Spring responde 204 sin cuerpo; las excepciones siguen su propio manejo.
    @ResponseStatus(HttpStatus.NO_CONTENT)
    // Solicita una baja lógica: desactiva el recurso y conserva su historial. El id lo toma Spring de la ruta.
    public void deactivate(@PathVariable long id) {
        // Solicita una baja lógica: desactiva el recurso y conserva su historial.
        service.deactivate(id);
    }
}

package com.acme.operations.common;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Set;

// Spring atiende las rutas de esta clase y serializa los valores devueltos como JSON.
@RestController
// Prefijo común de las rutas; se combina con el mapping de cada método.
@RequestMapping("/api/v1/audit")
// Spring Security exige ADMIN al entrar por el proxy antes de ejecutar esta operación.
@PreAuthorize("hasRole('ADMIN')")
public class AuditController {
    // JdbcTemplate ejecuta nuestro SQL y adapta filas; no decide las reglas de negocio.
    private final JdbcTemplate jdbc;

    // Spring suministra las dependencias a este único constructor; no hace falta escribir @Autowired.
    public AuditController(JdbcTemplate jdbc) {
        // Conserva la referencia recibida para que los métodos puedan reutilizar esta dependencia.
        this.jdbc = jdbc;
    }

    // DTO de salida del historial: quién hizo qué, sobre qué recurso y en qué petición.
    public record AuditView(
            // Identificador propio de este registro, distinto de las referencias a cliente o producto.
            long id,
            // Identidad que ejecutó la acción auditada.
            String actor,
            // Código de la acción que quedó registrada.
            String action,
            // Tipo de recurso afectado, por ejemplo ORDER o USER.
            String entityType,
            // Identificador del recurso afectado, guardado como texto.
            String entityId,
            // Descripción de la acción para seguir el historial.
            String details,
            // Identificador que conecta este evento con los logs de la petición.
            String requestId,
            // Instante en que se creó el registro.
            OffsetDateTime createdAt) {}

    // Expone una consulta HTTP GET; Spring selecciona este método para la ruta indicada.
    @GetMapping
    // Consulta una página con los filtros recibidos; no crea ni modifica registros.
    public PageResponse<AuditView> list(
            // Lee page de la URL: 0 es la primera página; usa el valor por defecto si se omite.
            @RequestParam(defaultValue = "0") int page,
            // Lee cuántos resultados se piden por página; las restricciones indicadas limitan ese valor.
            @RequestParam(defaultValue = "20") int size,
            // Lee un filtro opcional de la URL; si se omite, llega null.
            @RequestParam(required = false) String entityType,
            // Lee un filtro opcional de la URL; si se omite, llega null.
            @RequestParam(required = false) String entityId,
            // Lee un filtro opcional de la URL; si se omite, llega null.
            @RequestParam(required = false) String requestId) {
        // Reutiliza la validación de límites; esta consulta JDBC construye su propia paginación.
        PageRequests.of(page, size, "id", "desc", Set.of("id"));
        // Inicia un filtro siempre verdadero para poder añadir condiciones AND opcionales.
        StringBuilder filter = new StringBuilder(" WHERE true");
        // Guarda los valores para sustituir los ? sin concatenarlos dentro del SQL.
        var args = new ArrayList<Object>();
        // Filtra por tipo de recurso solo si la petición lo indica.
        if (entityType != null) {
            filter.append(" AND entity_type=?");
            args.add(entityType);
        }
        // Permite localizar las acciones sobre un id concreto.
        if (entityId != null) {
            filter.append(" AND entity_id=?");
            args.add(entityId);
        }
        // Permite seguir todas las acciones de una misma petición.
        if (requestId != null) {
            filter.append(" AND request_id=?");
            args.add(requestId);
        }
        // Cuenta coincidencias antes de añadir parámetros de paginación.
        long total =
                jdbc.queryForObject(
                        "SELECT count(*) FROM audit_event" + filter, Long.class, args.toArray());
        // LIMIT recibe size y OFFSET page*size; la primera página es cero.
        args.add(size);
        args.add((long) page * size);
        // Consulta los eventos más recientes y transforma cada fila en AuditView.
        var rows =
                jdbc.query(
                        "SELECT * FROM audit_event" + filter + " ORDER BY id DESC LIMIT ? OFFSET ?",
                        // El mapper lee columnas por nombre para separar el SQL del DTO de respuesta.
                        (rs, n) ->
                                new AuditView(
                                        rs.getLong("id"),
                                        rs.getString("actor"),
                                        rs.getString("action"),
                                        rs.getString("entity_type"),
                                        rs.getString("entity_id"),
                                        rs.getString("details"),
                                        rs.getString("request_id"),
                                        rs.getObject("created_at", OffsetDateTime.class)),
                        args.toArray());
        // Devuelve el historial y el número total de páginas.
        return new PageResponse<>(rows, page, size, total, (int) ((total + size - 1) / size));
    }
}

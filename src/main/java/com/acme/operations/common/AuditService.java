package com.acme.operations.common;

import org.slf4j.MDC;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Spring registra esta clase como servicio y permite inyectarla; las reglas las implementan sus métodos.
@Service
public class AuditService {
    // JdbcTemplate ejecuta nuestro SQL y adapta filas; no decide las reglas de negocio.
    private final JdbcTemplate jdbc;

    // Spring suministra las dependencias a este único constructor; no hace falta escribir @Autowired.
    public AuditService(JdbcTemplate jdbc) {
        // Conserva la referencia recibida para que los métodos puedan reutilizar esta dependencia.
        this.jdbc = jdbc;
    }

    // Al entrar por el proxy, Spring abre o comparte transacción; confirma al terminar y revierte ante errores no
    // comprobados.
    @Transactional
    // Registra una acción usando la identidad de la petición actual.
    public void record(String action, String entityType, String entityId, String details) {
        // Consulta el contexto de Spring Security; no recibe el nombre del usuario desde el cliente.
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        // Si no hay autenticación usa system, por ejemplo en un proceso de arranque.
        String actor = authentication == null ? "system" : authentication.getName();
        // Llamada interna: usa la transacción ya abierta de record, sin volver a pasar por el proxy.
        recordAs(actor, action, entityType, entityId, details);
    }

    // Al entrar por el proxy, Spring abre o comparte transacción; confirma al terminar y revierte ante errores no
    // comprobados.
    @Transactional
    // Permite indicar el actor explícitamente, como en login antes de autenticar la petición.
    public void recordAs(
            String actor, String action, String entityType, String entityId, String details) {
        // Inserta acción, recurso, detalle y requestId en la misma transacción del llamador.
        jdbc.update(
                "INSERT INTO audit_event(actor,action,entity_type,entity_id,details,request_id)"
                    + " VALUES (?,?,?,?,?,?)",
                actor,
                action,
                entityType,
                entityId,
                details,
                // El MDC aporta la correlación con logs; puede ser null fuera de una petición HTTP.
                MDC.get("requestId"));
    }
}

package com.acme.operations.customer;

import com.acme.operations.common.*;

import jakarta.persistence.criteria.Predicate;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Locale;
import java.util.Set;

// Spring registra esta clase como servicio y permite inyectarla; las reglas las implementan sus métodos.
@Service
// Spring configura lectura por defecto; readOnly orienta la persistencia, no sustituye permisos.
@Transactional(readOnly = true)
public class CustomerService {
    // Acceso a persistencia separado de las reglas que coordina este servicio.
    private final CustomerRepository repository;
    // Servicio de auditoría compartido para registrar las acciones de esta operación.
    private final AuditService audit;

    // Spring suministra las dependencias a este único constructor; no hace falta escribir @Autowired.
    public CustomerService(CustomerRepository repository, AuditService audit) {
        // Conserva la referencia recibida para que los métodos puedan reutilizar esta dependencia.
        this.repository = repository;
        // Conserva la referencia recibida para que los métodos puedan reutilizar esta dependencia.
        this.audit = audit;
    }

    // Lista clientes con filtros y orden permitido, conservando los totales de página.
    public PageResponse<CustomerDtos.View> list(
            int page, int size, String sort, String direction, String search, Boolean active) {
        // Valida límites y permite ordenar solo por id o nombre.
        var pageable = PageRequests.of(page, size, sort, direction, Set.of("id", "name"));
        // Rechaza un texto de búsqueda excesivamente largo.
        if (search != null && search.length() > 200)
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_SEARCH",
                    "Search is limited to 200 characters");
        // Spring Data ejecuta la consulta descrita por esta Specification.
        var result =
                repository.findAll(
                        (root, query, cb) -> {
                            // Reúne las condiciones opcionales que se aplicarán al SQL generado.
                            var predicates = new ArrayList<Predicate>();
                            // Solo filtra por active cuando se envió ese parámetro.
                            if (active != null)
                                predicates.add(cb.notEqual(root.get("active"), active));
                            // Una búsqueda no vacía añade una comparación parcial sin distinguir mayúsculas.
                            if (search != null && !search.isBlank()) {
                                // Escapa los comodines para buscar el texto literalmente dentro de nombre o taxId.
                                String pattern =
                                        "%"
                                                + search.toLowerCase(Locale.ROOT)
                                                        .replace("!", "!!")
                                                        .replace("%", "!%")
                                                        .replace("_", "!_")
                                                + "%";
                                // El cliente coincide si su nombre o identificador fiscal contiene el patrón.
                                predicates.add(
                                        cb.or(
                                                cb.like(cb.lower(root.get("name")), pattern, '!'),
                                                cb.like(
                                                        cb.lower(root.get("taxId")),
                                                        pattern,
                                                        '!')));
                            }
                            // Une los filtros con AND; cada condición añadida debe cumplirse.
                            return cb.and(predicates.toArray(Predicate[]::new));
                        },
                        pageable);
        // Convierte entidades a DTO y conserva tamaño, página y conteos.
        return PageResponse.from(result.map(CustomerDtos.View::of));
    }

    // Recupera un cliente; find genera 404 si el id no existe.
    public CustomerDtos.View get(long id) {
        // Construye la vista pública con los datos de la entidad.
        return CustomerDtos.View.of(find(id));
    }

    // Al entrar por el proxy, Spring abre o comparte transacción; confirma al terminar y revierte ante errores no
    // comprobados.
    @Transactional
    // Spring Security permite esta operación solo a ADMIN u OPERATIONS.
    @PreAuthorize("hasAnyRole('ADMIN','OPERATIONS')")
    // Crea un cliente usando las reglas de normalización de Customer.
    public CustomerDtos.View create(CustomerDtos.Input input) {
        // Inserta y sincroniza para obtener el id; aún falta el commit de la transacción.
        var entity = repository.saveAndFlush(new Customer(input));

        // Registra la acción junto con los cambios; si la transacción se revierte, también se deshace su auditoría.
        audit.record("CUSTOMER_CREATED", "CUSTOMER", entity.getId().toString(), "Created customer");
        // Entrega el DTO de la cuenta creada, sin exponer una entidad JPA al controlador.
        return CustomerDtos.View.of(entity);
    }

    // Al entrar por el proxy, Spring abre o comparte transacción; confirma al terminar y revierte ante errores no
    // comprobados.
    @Transactional
    // Spring Security permite esta operación solo a ADMIN u OPERATIONS.
    @PreAuthorize("hasAnyRole('ADMIN','OPERATIONS')")
    // Actualiza datos del cliente con control de versión.
    public CustomerDtos.View update(long id, CustomerDtos.Input input) {
        // Carga la entidad gestionada que JPA supervisará dentro de la transacción.
        var entity = find(id);
        // Actualizar requiere enviar la versión leída por el cliente.
        if (input.version() == null)
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST, "VERSION_REQUIRED", "Provide current resource version");
        // Si la versión cambió, devuelve 409 para evitar sobrescribir datos recientes.
        if (input.version() != entity.getVersion())
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "CONCURRENT_MODIFICATION",
                    "Resource changed; reload and retry");
        // Aplica los datos a la entidad; la persistencia se sincroniza a continuación.
        entity.update(input);
        // Envía los cambios y permite detectar conflictos mediante @Version.
        repository.flush();
        // Registra la acción junto con los cambios; si la transacción se revierte, también se deshace su auditoría.
        audit.record("CUSTOMER_UPDATED", "CUSTOMER", Long.toString(id), "Updated customer");
        // Devuelve la vista del cliente ya sincronizada.
        return CustomerDtos.View.of(entity);
    }

    // Al entrar por el proxy, Spring abre o comparte transacción; confirma al terminar y revierte ante errores no
    // comprobados.
    @Transactional
    // Spring Security permite esta operación solo a ADMIN u OPERATIONS.
    @PreAuthorize("hasAnyRole('ADMIN','OPERATIONS')")
    // Desactiva sin borrar para conservar los pedidos vinculados al cliente.
    public void deactivate(long id) {
        // Carga la entidad o lanza 404 si el cliente no existe.
        var entity = find(id);
        // Evita repetir cambios y auditoría cuando ya estaba desactivado.
        if (entity.isActive()) {
            // Marca active=false en memoria dentro de la transacción.
            entity.deactivate();
            // JPA sincroniza ese cambio con la base de datos.
            repository.flush();
            // Registra la acción junto con los cambios; si la transacción se revierte, también se deshace su auditoría.
            audit.record(
                    "CUSTOMER_DEACTIVATED", "CUSTOMER", Long.toString(id), "Deactivated customer");
        }
    }

    // Centraliza la búsqueda y el error de cliente inexistente.
    private Customer find(long id) {
        // El Optional de Spring Data se convierte en entidad o en una excepción 404.
        return repository
                .findById(id)
                .orElseThrow(
                        () ->
                                new BusinessException(
                                        HttpStatus.NOT_FOUND,
                                        "CUSTOMER_NOT_FOUND",
                                        "Customer not found"));
    }
}

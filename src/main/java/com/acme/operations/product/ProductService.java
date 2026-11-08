package com.acme.operations.product;

import com.acme.operations.common.*;

import jakarta.persistence.criteria.Predicate;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
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
public class ProductService {
    // Acceso a persistencia separado de las reglas que coordina este servicio.
    private final ProductRepository repository;
    // Servicio de auditoría compartido para registrar las acciones de esta operación.
    private final AuditService audit;
    // JdbcTemplate ejecuta nuestro SQL y adapta filas; no decide las reglas de negocio.
    private final JdbcTemplate jdbc;

    // Spring suministra las dependencias a este único constructor; no hace falta escribir @Autowired.
    public ProductService(ProductRepository repository, AuditService audit, JdbcTemplate jdbc) {
        // Conserva la referencia recibida para que los métodos puedan reutilizar esta dependencia.
        this.repository = repository;
        // Conserva la referencia recibida para que los métodos puedan reutilizar esta dependencia.
        this.audit = audit;
        // Conserva la referencia recibida para que los métodos puedan reutilizar esta dependencia.
        this.jdbc = jdbc;
    }

    // Lista productos aplicando filtros y paginación, sin modificar el catálogo.
    public PageResponse<ProductDtos.View> list(
            int page, int size, String sort, String direction, String search, Boolean active) {
        // Valida tamaño, página y columnas permitidas antes de construir el Pageable.
        var pageable =
                PageRequests.of(page, size, sort, direction, Set.of("id", "name", "unitPrice"));
        // Limita la longitud de búsqueda para mantener controlada la entrada.
        if (search != null && search.length() > 200)
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_SEARCH",
                    "Search is limited to 200 characters");
        // Spring Data ejecuta la consulta que describe esta Specification.
        var result =
                repository.findAll(
                        (root, query, cb) -> {
                            // Acumula condiciones; solo se añaden los filtros que llegaron en la petición.
                            var predicates = new ArrayList<Predicate>();
                            // Boolean permite distinguir entre sin filtro (null), activo y desactivado.
                            if (active != null)
                                predicates.add(cb.equal(root.get("active"), active));
                            // Ignora búsquedas vacías; una búsqueda real se compara sin distinguir mayúsculas.
                            if (search != null && !search.isBlank()) {
                                // Escapa !, % y _ para que el texto del usuario no se interprete como comodines SQL.
                                String pattern =
                                        "%"
                                                + search.toLowerCase(Locale.ROOT)
                                                        .replace("!", "!!")
                                                        .replace("%", "!%")
                                                + "%";
                                // Busca coincidencias en nombre o SKU; basta con que coincida uno de ellos.
                                predicates.add(
                                        cb.or(
                                                cb.like(cb.lower(root.get("name")), pattern, '!'),
                                                cb.like(cb.lower(root.get("sku")), pattern, '!')));
                            }
                            // Combina los filtros con AND; si no hay filtros no restringe los resultados.
                            return cb.and(predicates.toArray(Predicate[]::new));
                        },
                        pageable);
        // Transforma entidades a DTO públicos y conserva los totales de paginación.
        return PageResponse.from(result.map(ProductDtos.View::of));
    }

    // Busca el producto por id y devuelve su representación de API.
    public ProductDtos.View get(long id) {
        // find lanza 404 si falta; View.of copia los campos que se enviarán al cliente.
        return ProductDtos.View.of(find(id));
    }

    // Al entrar por el proxy, Spring abre o comparte transacción; confirma al terminar y revierte ante errores no
    // comprobados.
    @Transactional
    // Spring Security permite esta operación solo a ADMIN u OPERATIONS.
    @PreAuthorize("hasAnyRole('ADMIN','OPERATIONS')")
    // Crea catálogo y saldo inicial en una sola transacción.
    public ProductDtos.View create(ProductDtos.Input input) {
        // saveAndFlush envía el INSERT y obtiene el id; flush todavía no es commit.
        var entity = repository.saveAndFlush(new Product(input));
        // Crea el inventario del producto con físico, reservado y versión en cero.
        jdbc.update(
                "INSERT INTO inventory_balance(product_id,on_hand,reserved,version) VALUES"
                    + " (?,0,0,0)",
                entity.getId());
        // Registra la acción junto con los cambios; si la transacción se revierte, también se deshace su auditoría.
        audit.record("PRODUCT_CREATED", "PRODUCT", entity.getId().toString(), "Created product");
        // Devuelve la vista creada; Spring confirma al salir correctamente del método transaccional.
        return ProductDtos.View.of(entity);
    }

    // Al entrar por el proxy, Spring abre o comparte transacción; confirma al terminar y revierte ante errores no
    // comprobados.
    @Transactional
    // Spring Security permite esta operación solo a ADMIN u OPERATIONS.
    @PreAuthorize("hasAnyRole('ADMIN','OPERATIONS')")
    // Actualiza el producto solo si la versión del cliente sigue siendo válida.
    public ProductDtos.View update(long id, ProductDtos.Input input) {
        // Carga la entidad gestionada por JPA; si no existe, se devuelve 404.
        var entity = find(id);
        // Para actualizar se exige la versión; para crear el DTO permite omitirla.
        if (input.version() == null)
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST, "VERSION_REQUIRED", "Provide current resource version");
        // Detecta que el cliente está editando una versión antigua del producto.
        if (input.version() != entity.getVersion())
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "CONCURRENT_MODIFICATION",
                    "Resource changed; reload and retry");
        // Modifica la entidad gestionada; JPA detectará sus cambios al sincronizar.
        entity.update(input);
        // Envía el UPDATE; @Version permite que JPA detecte otra escritura concurrente.
        repository.flush();
        // Registra la acción junto con los cambios; si la transacción se revierte, también se deshace su auditoría.
        audit.record("PRODUCT_UPDATED", "PRODUCT", Long.toString(id), "Updated product");
        // Devuelve los campos y versión después de sincronizar la actualización.
        return ProductDtos.View.of(entity);
    }

    // Al entrar por el proxy, Spring abre o comparte transacción; confirma al terminar y revierte ante errores no
    // comprobados.
    @Transactional
    // Spring Security permite esta operación solo a ADMIN u OPERATIONS.
    @PreAuthorize("hasAnyRole('ADMIN','OPERATIONS')")
    // Desactiva sin borrar el producto para conservar referencias e historial.
    public void deactivate(long id) {
        // Carga la entidad que se va a desactivar.
        var entity = find(id);
        // Si ya estaba desactivada no repite escritura ni auditoría.
        if (entity.isActive()) {
            // Cambia active a false en la entidad gestionada.
            entity.deactivate();
            // Sincroniza el cambio con PostgreSQL, todavía dentro de la transacción.
            repository.flush();
            // Registra la acción junto con los cambios; si la transacción se revierte, también se deshace su auditoría.
            audit.record(
                    "PRODUCT_DEACTIVATED", "PRODUCT", Long.toString(id), "Deactivated product");
        }
    }

    // Centraliza la búsqueda y el error cuando no existe el producto.
    private Product find(long id) {
        // Spring Data busca por clave primaria; el Optional vacío se convierte en 404.
        return repository
                .findById(id)
                .orElseThrow(
                        () ->
                                new BusinessException(
                                        HttpStatus.NOT_FOUND,
                                        "PRODUCT_NOT_FOUND",
                                        "Product not found"));
    }
}

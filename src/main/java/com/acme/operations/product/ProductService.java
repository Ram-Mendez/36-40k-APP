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

// Spring registers this class as a service. Its methods implement the business rules.
@Service
// Use read-only transactions by default. Spring Security checks access separately.
@Transactional(readOnly = true)
public class ProductService {
    // The repository handles persistence for this service.
    private final ProductRepository repository;
    // Use the shared audit service to record this operation.
    private final AuditService audit;
    // JdbcTemplate executes the SQL and maps the result rows.
    private final JdbcTemplate jdbc;

    // Spring injects the dependencies through the single constructor.
    public ProductService(ProductRepository repository, AuditService audit, JdbcTemplate jdbc) {
        // Keep the injected dependency for later calls.
        this.repository = repository;
        // Keep the injected dependency for later calls.
        this.audit = audit;
        // Keep the injected dependency for later calls.
        this.jdbc = jdbc;
    }

    // List of products using filters and paging, without changing the catalogue.
    public PageResponse<ProductDtos.View> list(
            int page, int size, String sort, String direction, String search, Boolean active) {
        // Valid size, page and columns allowed before building the Pageable.
        var pageable =
                PageRequests.of(page, size, sort, direction, Set.of("id", "name", "unitPrice", "sku", "active"));
        // Limit the search length to keep the input under control.
        if (search != null && search.length() > 200)
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_SEARCH",
                    "Search is limited to 200 characters");
        // Spring Data runs the query that describes this Specification.
        var result =
                repository.findAll(
                        (root, query, cb) -> {
                            // It accumulates conditions; only the filters that arrived in the request are added.
                            var predicates = new ArrayList<Predicate>();
                            // Boolean allows to distinguish between no filter (null), active and disabled.
                            if (active != null)
                                predicates.add(cb.equal(root.get("active"), active));
                            // It ignores empty searches; a real search is compared without distinguishing capital
                            // letters.
                            if (search != null && !search.isBlank()) {
                                // Escape !, % and _ so user input is treated as literal text in SQL.
                                String pattern =
                                        "%"
                                                + search.toLowerCase(Locale.ROOT)
                                                        .replace("!", "!!")
                                                        .replace("%", "!%")
                                                        .replace("_", "!_")
                                                + "%";
                                // It seeks coincidences on behalf of or SKU; it simply matches one of them.
                                predicates.add(
                                        cb.or(
                                                cb.like(cb.lower(root.get("name")), pattern, '!'),
                                                cb.like(cb.lower(root.get("sku")), pattern, '!')));
                            }
                            // Combine filters with AND; if there are no filters it does not restrict the results.
                            return cb.and(predicates.toArray(Predicate[]::new));
                        },
                        pageable);
        // Map the entities to public DTOs and preserve the pagination totals.
        return PageResponse.from(result.map(ProductDtos.View::of));
    }

    // Find the product per id and return your API representation.
    public ProductDtos.View get(long id) {
        // find launches 404 if missing; View.of copies the fields to be sent to the customer.
        return ProductDtos.View.of(find(id));
    }

    // Spring starts or joins a transaction when the call goes through the proxy. It commits on success and rolls
    // back on unchecked exceptions.
    @Transactional
    // Only ADMIN and OPERATIONS can perform this operation.
    @PreAuthorize("hasAnyRole('ADMIN','OPERATIONS')")
    // Create catalog and start balance in a single transaction.
    public ProductDtos.View create(ProductDtos.Input input) {
        // saveAndFlush sends the INSERT and gets the id; flush is not yet committed.
        var entity = repository.saveAndFlush(new Product(input));
        // It creates the product inventory with physical, reserved and zero version.
        jdbc.update(
                "INSERT INTO inventory_balance(product_id,on_hand,reserved,version) VALUES"
                    + " (?,0,0,0)",
                entity.getId());
        // Write the audit event in the same transaction as the change. Both roll back together.
        audit.record("PRODUCT_CREATED", "PRODUCT", entity.getId().toString(), "Created product");
        // Returns the created view; Spring confirms the correct exit of the transactional method.
        return ProductDtos.View.of(entity);
    }

    // Spring starts or joins a transaction when the call goes through the proxy. It commits on success and rolls
    // back on unchecked exceptions.
    @Transactional
    // Only ADMIN and OPERATIONS can perform this operation.
    @PreAuthorize("hasAnyRole('ADMIN','OPERATIONS')")
    // It updates the product only if the customer's version remains valid.
    public ProductDtos.View update(long id, ProductDtos.Input input) {
        // Load the entity managed by JPA; if it does not exist, 404 is returned.
        var entity = find(id);
        // To update the version is required; to create the DTO it allows to omit it.
        if (input.version() == null)
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST, "VERSION_REQUIRED", "Provide current resource version");
        // It detects that the customer is editing an old version of the product.
        if (input.version() != entity.getVersion())
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "CONCURRENT_MODIFICATION",
                    "Resource changed; reload and retry");
        // Modify the managed entity; JPA will detect its changes when synchronizing.
        entity.update(input);
        // Send the UPDATE; @Version allows JPA to detect another concurrent writing.
        repository.flush();
        // Write the audit event in the same transaction as the change. Both roll back together.
        audit.record("PRODUCT_UPDATED", "PRODUCT", Long.toString(id), "Updated product");
        // Returns the fields and version after synchronizing the update.
        return ProductDtos.View.of(entity);
    }

    // Spring starts or joins a transaction when the call goes through the proxy. It commits on success and rolls
    // back on unchecked exceptions.
    @Transactional
    // Only ADMIN and OPERATIONS can perform this operation.
    @PreAuthorize("hasAnyRole('ADMIN','OPERATIONS')")
    // Disable without deleting the product to keep references and history.
    public void deactivate(long id) {
        // Load the entity that will be deactivated.
        var entity = find(id);
        // If it was already disabled, it does not repeat writing or auditing.
        if (entity.isActive()) {
            // It changes active to false in the managed entity.
            entity.deactivate();
            // Synchronize the change with PostgreSQL, still within the transaction.
            repository.flush();
            // Write the audit event in the same transaction as the change. Both roll back together.
            audit.record(
                    "PRODUCT_DEACTIVATED", "PRODUCT", Long.toString(id), "Deactivated product");
        }
    }

    // Centralize the search and error when the product does not exist.
    private Product find(long id) {
        // Spring Data seeks for primary key; the Optional vacuum becomes 404.
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

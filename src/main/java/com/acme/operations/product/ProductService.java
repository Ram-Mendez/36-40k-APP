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

@Service
@Transactional(readOnly = true)
public class ProductService {
    private final ProductRepository repository;
    private final AuditService audit;
    private final JdbcTemplate jdbc;

    public ProductService(ProductRepository repository, AuditService audit, JdbcTemplate jdbc) {
        this.repository = repository;
        this.audit = audit;
        this.jdbc = jdbc;
    }

    public PageResponse<ProductDtos.View> list(
            int page, int size, String sort, String direction, String search, Boolean active) {
        var pageable =
                PageRequests.of(page, size, sort, direction, Set.of("id", "name", "unitPrice"));
        if (search != null && search.length() > 200)
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_SEARCH",
                    "Search is limited to 200 characters");
        var result =
                repository.findAll(
                        (root, query, cb) -> {
                            var predicates = new ArrayList<Predicate>();
                            if (active != null)
                                predicates.add(cb.equal(root.get("active"), active));
                            if (search != null && !search.isBlank()) {
                                String pattern =
                                        "%"
                                                + search.toLowerCase(Locale.ROOT)
                                                        .replace("!", "!!")
                                                        .replace("%", "!%")
                                                        .replace("_", "!_")
                                                + "%";
                                predicates.add(
                                        cb.or(
                                                cb.like(cb.lower(root.get("name")), pattern, '!'),
                                                cb.like(cb.lower(root.get("sku")), pattern, '!')));
                            }
                            return cb.and(predicates.toArray(Predicate[]::new));
                        },
                        pageable);
        return PageResponse.from(result.map(ProductDtos.View::of));
    }

    public ProductDtos.View get(long id) {
        return ProductDtos.View.of(find(id));
    }

    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN','OPERATIONS')")
    public ProductDtos.View create(ProductDtos.Input input) {
        var entity = repository.saveAndFlush(new Product(input));
        jdbc.update(
                "INSERT INTO inventory_balance(product_id,on_hand,reserved,version) VALUES"
                    + " (?,0,0,0)",
                entity.getId());
        audit.record("PRODUCT_CREATED", "PRODUCT", entity.getId().toString(), "Created product");
        return ProductDtos.View.of(entity);
    }

    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN','OPERATIONS')")
    public ProductDtos.View update(long id, ProductDtos.Input input) {
        var entity = find(id);
        if (input.version() == null)
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST, "VERSION_REQUIRED", "Provide current resource version");
        if (input.version() != entity.getVersion())
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "CONCURRENT_MODIFICATION",
                    "Resource changed; reload and retry");
        entity.update(input);
        repository.flush();
        audit.record("PRODUCT_UPDATED", "PRODUCT", Long.toString(id), "Updated product");
        return ProductDtos.View.of(entity);
    }

    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN','OPERATIONS')")
    public void deactivate(long id) {
        var entity = find(id);
        if (entity.isActive()) {
            entity.deactivate();
            repository.flush();
            audit.record(
                    "PRODUCT_DEACTIVATED", "PRODUCT", Long.toString(id), "Deactivated product");
        }
    }

    private Product find(long id) {
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

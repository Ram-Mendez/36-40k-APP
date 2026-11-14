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

// Spring registers this class as a service. Its methods implement the business rules.
@Service
// Use read-only transactions by default. Spring Security checks access separately.
@Transactional(readOnly = true)
public class CustomerService {
    // The repository handles persistence for this service.
    private final CustomerRepository repository;
    // Use the shared audit service to record this operation.
    private final AuditService audit;

    // Spring injects the dependencies through the single constructor.
    public CustomerService(CustomerRepository repository, AuditService audit) {
        // Keep the injected dependency for later calls.
        this.repository = repository;
        // Keep the injected dependency for later calls.
        this.audit = audit;
    }

    // List customers with filters and allowed order, keeping the page totals.
    public PageResponse<CustomerDtos.View> list(
            int page, int size, String sort, String direction, String search, Boolean active) {
        // Valid limits and allows to order only by id or name.
        var pageable = PageRequests.of(page, size, sort, direction, Set.of("id", "name"));
        // It rejects an excessively long search text.
        if (search != null && search.length() > 200)
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_SEARCH",
                    "Search is limited to 200 characters");
        // Spring Data runs the query described by this Specification.
        var result =
                repository.findAll(
                        (root, query, cb) -> {
                            // Gather the optional conditions to be applied to the generated SQL.
                            var predicates = new ArrayList<Predicate>();
                            // It only filters per active when that parameter was sent.
                            if (active != null)
                                predicates.add(cb.equal(root.get("active"), active));
                            // A non-empty search adds a partial comparison without distinguishing capital letters.
                            if (search != null && !search.isBlank()) {
                                // Escape SQL wildcards so the search matches literal text in the name or tax ID.
                                String pattern =
                                        "%"
                                                + search.toLowerCase(Locale.ROOT)
                                                        .replace("!", "!!")
                                                        .replace("%", "!%")
                                                        .replace("_", "!_")
                                                + "%";
                                // The client agrees if his name or tax identifier contains the pattern.
                                predicates.add(
                                        cb.or(
                                                cb.like(cb.lower(root.get("name")), pattern, '!'),
                                                cb.like(
                                                        cb.lower(root.get("taxId")),
                                                        pattern,
                                                        '!')));
                            }
                            // Unite filters with AND; each added condition must be met.
                            return cb.and(predicates.toArray(Predicate[]::new));
                        },
                        pageable);
        // Convert entities to DTO and retain size, page and counting.
        return PageResponse.from(result.map(CustomerDtos.View::of));
    }

    // It recovers a client; find generates 404 if the id does not exist.
    public CustomerDtos.View get(long id) {
        // Build public view with entity data.
        return CustomerDtos.View.of(find(id));
    }

    // Spring starts or joins a transaction when the call goes through the proxy. It commits on success and rolls
    // back on unchecked exceptions.
    @Transactional
    // Only ADMIN and OPERATIONS can perform this operation.
    @PreAuthorize("hasAnyRole('ADMIN','OPERATIONS')")
    // Create a client using Customer's normalization rules.
    public CustomerDtos.View create(CustomerDtos.Input input) {
        // Insert and synchronize to get the id; the transaction commitment is still missing.
        var entity = repository.saveAndFlush(new Customer(input));

        // Write the audit event in the same transaction as the change. Both roll back together.
        audit.record("CUSTOMER_CREATED", "CUSTOMER", entity.getId().toString(), "Created customer");
        // Deliver the DTO of the account created, without exposing a JPA entity to the controller.
        return CustomerDtos.View.of(entity);
    }

    // Spring starts or joins a transaction when the call goes through the proxy. It commits on success and rolls
    // back on unchecked exceptions.
    @Transactional
    // Only ADMIN and OPERATIONS can perform this operation.
    @PreAuthorize("hasAnyRole('ADMIN','OPERATIONS')")
    // Update customer data with version control.
    public CustomerDtos.View update(long id, CustomerDtos.Input input) {
        // Load the managed entity that JPA will supervise within the transaction.
        var entity = find(id);
        // Update requires sending the version read by the client.
        if (input.version() == null)
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST, "VERSION_REQUIRED", "Provide current resource version");
        // If the version changed, it returns 409 to avoid overwriting recent data.
        if (input.version() != entity.getVersion())
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "CONCURRENT_MODIFICATION",
                    "Resource changed; reload and retry");
        // Apply the data to the entity; persistence is synchronized below.
        entity.update(input);
        // It sends the changes and allows you to detect conflicts by @Version.
        repository.flush();
        // Write the audit event in the same transaction as the change. Both roll back together.
        audit.record("CUSTOMER_UPDATED", "CUSTOMER", Long.toString(id), "Updated customer");
        // Returns the customer's view already synchronized.
        return CustomerDtos.View.of(entity);
    }

    // Spring starts or joins a transaction when the call goes through the proxy. It commits on success and rolls
    // back on unchecked exceptions.
    @Transactional
    // Only ADMIN and OPERATIONS can perform this operation.
    @PreAuthorize("hasAnyRole('ADMIN','OPERATIONS')")
    // Undeleted disable to keep orders linked to the customer.
    public void deactivate(long id) {
        // Load the entity or launch 404 if the customer does not exist.
        var entity = find(id);
        // It avoids repeating changes and audit when it was already off.
        if (entity.isActive()) {
            // Active mark = misplaced in memory within the transaction.
            entity.deactivate();
            // JPA synchronizes that change with the database.
            repository.flush();
            // Write the audit event in the same transaction as the change. Both roll back together.
            audit.record(
                    "CUSTOMER_DEACTIVATED", "CUSTOMER", Long.toString(id), "Deactivated customer");
        }
    }

    // It centralizes the search and error of non-existent customer.
    private Customer find(long id) {
        // The Spring Data Optional becomes an entity or an exception 404.
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

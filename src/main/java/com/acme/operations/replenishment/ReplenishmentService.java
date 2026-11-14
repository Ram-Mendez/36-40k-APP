package com.acme.operations.replenishment;

import com.acme.operations.common.AuditService;
import com.acme.operations.common.BusinessException;
import com.acme.operations.common.PageResponse;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
@Transactional(readOnly = true)
@PreAuthorize("hasAnyRole('ADMIN','OPERATIONS','READONLY')")
public class ReplenishmentService {
    private final ReplenishmentRepository repository;
    private final AuditService audit;

    public ReplenishmentService(ReplenishmentRepository repository, AuditService audit) {
        this.repository = repository;
        this.audit = audit;
    }

    public ReplenishmentDtos.Policy get(long productId) {
        return repository.find(productId).orElseThrow(() -> new BusinessException(
                HttpStatus.NOT_FOUND,"REPLENISHMENT_POLICY_NOT_FOUND","Replenishment policy not found"));
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResponse<ReplenishmentDtos.Policy> policies(
            Long productId, Long supplierId, Boolean active, String search, int page, int size) {
        return repository.policies(productId,supplierId,active,search,page,size);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResponse<ReplenishmentDtos.Proposal> proposals(
            Long productId, Long supplierId, String search, int page, int size) {
        return repository.proposals(productId,supplierId,search,page,size);
    }

    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN','OPERATIONS')")
    public ReplenishmentDtos.Policy upsert(long productId, ReplenishmentDtos.PolicyInput input) {
        if (input.targetStock() < input.minimumStock()) throw new BusinessException(
                HttpStatus.BAD_REQUEST,"INVALID_REPLENISHMENT_RANGE","Target stock must be at least minimum stock");
        boolean productActive = repository.productActive(productId);
        if (input.active() && !productActive) throw new BusinessException(
                HttpStatus.CONFLICT,"PRODUCT_UNAVAILABLE","An active policy requires an active product");
        var current = repository.find(productId);
        if (current.isPresent() && input.version() == null) throw new BusinessException(
                HttpStatus.BAD_REQUEST,"VERSION_REQUIRED","Provide current policy version");
        if (current.isEmpty() && input.version() != null) throw new BusinessException(
                HttpStatus.BAD_REQUEST,"VERSION_NOT_ALLOWED","Omit version when creating a policy");
        if (current.isPresent() && input.version() != current.get().version()) throw changed();

        // An existing association can remain after supplier deactivation; proposals expose its warning.
        boolean assigningSupplier = input.preferredSupplierId() != null
                && (current.isEmpty() || !Objects.equals(
                        current.get().preferredSupplierId(),input.preferredSupplierId()));
        if (assigningSupplier) repository.requireActiveSupplier(input.preferredSupplierId());
        boolean written = current.isEmpty() ? repository.create(productId,input) : repository.replace(productId,input);
        if (!written) throw changed();
        audit.record(current.isEmpty() ? "REPLENISHMENT_POLICY_CREATED" : "REPLENISHMENT_POLICY_UPDATED",
                "REPLENISHMENT_POLICY",Long.toString(productId),"Minimum "+input.minimumStock()+", target "+input.targetStock());
        return get(productId);
    }

    private static BusinessException changed() {
        return new BusinessException(HttpStatus.CONFLICT,"CONCURRENT_MODIFICATION","Policy changed; reload and retry");
    }
}

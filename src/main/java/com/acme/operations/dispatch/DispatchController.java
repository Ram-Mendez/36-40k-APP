package com.acme.operations.dispatch;

import com.acme.operations.product.ProductDtos;
import com.acme.operations.shipping.ShippingClient;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("turno-cero")
@RequestMapping("/api/v1/dispatch")
@PreAuthorize("hasAnyRole('ADMIN','OPERATIONS')")
class DispatchController {
    record Plan(Instant openedAt, Instant closesAt) {}
    record Parcel(@NotBlank String barcode, @Positive int units) {}
    record Scan(@NotNull Parcel parcel) {}
    record ScanReport(DispatchPlanning.Label label, int units) {}
    record Capacity(String wave, String dock, int maxParcels) {}
    record Seal(@NotNull UUID attemptId, @NotNull Boolean intact) {}
    record SealReport(UUID attemptId) {}
    record Catalog(int page, int totalPages, List<ProductDtos.View> products) {}

    private final DispatchPlanning planning;
    private final DispatchManifest manifest;
    private final DispatchRadio radio;

    DispatchController(DispatchPlanning planning, DispatchManifest manifest, DispatchRadio radio) {
        this.planning = planning;
        this.manifest = manifest;
        this.radio = radio;
    }

    @GetMapping("/plan")
    public Plan plan(@RequestParam Instant openedAt) {
        return new Plan(openedAt, planning.closeAt(openedAt));
    }

    @PostMapping("/scans")
    public ScanReport scan(@Valid @RequestBody Scan scan) {
        var parcel = scan.parcel();
        return new ScanReport(planning.label(parcel.barcode()), parcel.units());
    }

    @GetMapping("/labels")
    public DispatchPlanning.Label label(@RequestParam long barcode) {
        return planning.label(Long.toString(barcode));
    }

    @GetMapping("/capacity")
    public Capacity capacity(@RequestParam String wave, @RequestParam String dock) {
        return new Capacity(wave, dock, planning.capacity(wave, dock));
    }

    @PostMapping("/seals")
    public ResponseEntity<SealReport> seal(@Valid @RequestBody Seal seal)
            throws DispatchManifest.SealRejected {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new SealReport(manifest.seal(seal.attemptId(), seal.intact())));
    }

    @GetMapping("/manifests/{attemptId}")
    public DispatchManifest.Status status(@PathVariable UUID attemptId) {
        return manifest.status(attemptId);
    }

    @GetMapping("/quote")
    public ShippingClient.Quote quote(@RequestParam @Positive long orderId) {
        return planning.quote(orderId);
    }

    @GetMapping("/radio")
    public CompletableFuture<DispatchRadio.Signal> radio() {
        return radio.receive();
    }

    @GetMapping("/catalog")
    public Catalog catalog(@RequestParam @Positive int page) {
        var result = planning.catalog(page);
        return new Catalog(page, result.totalPages(), result.content());
    }

    @PostMapping("/manifests/{attemptId}/release")
    public DispatchManifest.Status release(@PathVariable UUID attemptId) {
        return manifest.release(attemptId);
    }

    @ExceptionHandler(DispatchManifest.SealRejected.class)
    public ProblemDetail sealRejected(DispatchManifest.SealRejected exception) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY,
                exception.getMessage());
        problem.setProperty("code", "SEAL_REJECTED");
        return problem;
    }

    @ExceptionHandler(DispatchManifest.LoadingBlocked.class)
    public ResponseEntity<ProblemDetail> loadingBlocked(DispatchManifest.LoadingBlocked exception) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
        problem.setProperty("code", "LOADING_BLOCKED");
        return ResponseEntity.ok(problem);
    }
}

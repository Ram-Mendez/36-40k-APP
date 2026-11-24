package com.acme.operations.warehouse;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;

@RestController
@Profile("dev & !turno-cero")
@Tag(name = "Traslados de almacén")
@PreAuthorize("hasAnyRole('ADMIN','OPERATIONS')")
@RequestMapping("/api/v1/warehouse-transfers")
class TransferPracticeController {
    private final TransferPracticeService service;
    private final TransferRouting.Router router;
    private final ApplicationEventPublisher events;

    TransferPracticeController(TransferPracticeService service, TransferRouting.Router router,
            ApplicationEventPublisher events) {
        this.service = service;
        this.router = router;
        this.events = events;
    }

    @GetMapping("/{code}")
    @Parameter(name = "If-None-Match", in = ParameterIn.HEADER, description = "ETag de la última lectura")
    public ResponseEntity<TransferPracticeDtos.Transfer> transfer(
            @PathVariable String code, WebRequest request) {
        var transfer = service.transfer(code);
        String etag = "\"" + transfer.code() + "\"";
        if (request.checkNotModified(etag)) {
            return ResponseEntity.status(304).eTag(etag).build();
        }
        return ResponseEntity.ok().eTag(etag).body(transfer);
    }

    @PatchMapping("/{code}")
    public TransferPracticeDtos.Transfer patch(@PathVariable String code, @RequestBody JsonNode body) {
        return service.patch(code, body);
    }

    @PostMapping("/{code}/inspection")
    public ResponseEntity<Void> inspect(@PathVariable String code,
            @Valid @RequestBody TransferPracticeDtos.Inspection body) {
        service.inspect(code, body.passed());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{code}/inspections")
    public List<TransferPracticeDtos.Attempt> inspections(@PathVariable String code) {
        return service.attempts(code);
    }

    @PostMapping("/{code}/dispatch")
    public TransferPracticeDtos.Transfer dispatch(@PathVariable String code) {
        var transfer = service.dispatch(code);
        events.publishEvent(new TransferDispatchOutbox.Dispatched(code));
        return transfer;
    }

    @GetMapping("/{code}/dispatch-events")
    public List<TransferPracticeDtos.Event> dispatchEvents(@PathVariable String code) {
        return service.dispatchEvents(code);
    }

    @PostMapping("/damage")
    public ResponseEntity<Void> damage(@Valid @RequestBody TransferPracticeDtos.Damage body) {
        service.recordDamage(body);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/damage")
    public List<TransferPracticeDtos.DamageBalance> damageBalances(@RequestParam String sku) {
        return service.damageBalances(sku);
    }

    @PostMapping("/docks/{dock}/bookings")
    public TransferPracticeDtos.Slot book(@PathVariable String dock,
            @Valid @RequestBody TransferPracticeDtos.Booking body) {
        return service.book(dock, body.startsAt(), body.endsAt());
    }

    @PostMapping("/queues/{queue}/claim-next")
    public TransferPracticeDtos.Job claimNext(@PathVariable String queue) {
        return service.claimNext(queue);
    }

    @PostMapping("/{code}/weight")
    public TransferPracticeDtos.Weight confirmWeight(@PathVariable String code,
            @Valid @RequestBody TransferPracticeDtos.Weight body) {
        return service.confirmWeight(code, body.kilograms());
    }

    @GetMapping("/lots/{lot}/count")
    public TransferPracticeDtos.LotCount lotCount(@PathVariable String lot) {
        return service.count(lot);
    }

    @PostMapping("/routing")
    public TransferPracticeDtos.Route route(@Valid @RequestBody TransferPracticeDtos.ReceiptCondition body) {
        return router.route(body);
    }
}

package com.acme.operations.reconciliation;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("dev & !turno-cero")
@PreAuthorize("hasAnyRole('ADMIN','OPERATIONS')")
@RequestMapping("/api/v1/warehouse-reconciliation")
@Tag(name = "Conciliación de almacén")
class WarehouseReconciliationController {
    private final WarehouseReconciliationService service;

    WarehouseReconciliationController(WarehouseReconciliationService service) {
        this.service = service;
    }

    @GetMapping("/balance/{sku}")
    public WarehouseReconciliationService.Balance balance(@PathVariable String sku,
            @RequestParam Instant cutoff) {
        return service.balance(sku, cutoff);
    }

    @GetMapping("/eligible-lots/{sku}")
    public List<WarehouseReconciliationService.Lot> eligibleLots(@PathVariable String sku,
            @RequestParam LocalDate departure, @RequestParam LocalDate arrival,
            @RequestParam @Min(0) @Max(365) int minimumDays) {
        return service.eligibleLots(sku, departure, arrival, minimumDays);
    }

    @GetMapping("/fifo/{sku}")
    public List<WarehouseReconciliationService.Pick> fifo(@PathVariable String sku,
            @RequestParam @Min(1) @Max(10000) int requested) {
        return service.fifo(sku, requested);
    }

    @GetMapping("/serials/{manifest}")
    public WarehouseReconciliationService.SerialCheck serials(@PathVariable String manifest) {
        return service.serialCheck(manifest);
    }

    @GetMapping("/kits/{kit}")
    public WarehouseReconciliationService.KitPlan kits(@PathVariable String kit) {
        return service.kitPlan(kit);
    }

    @GetMapping("/allocation/{batch}")
    public WarehouseReconciliationService.AllocationPlan allocation(@PathVariable String batch) {
        return service.allocate(batch);
    }

    @GetMapping("/dock-visits/{batch}")
    public List<WarehouseReconciliationService.DockVisit> dockVisits(@PathVariable String batch) {
        return service.dockVisits(batch);
    }

    @GetMapping("/blocked-by/{code}")
    public List<String> blockedBy(@PathVariable String code) {
        return service.blockedBy(code);
    }

    @GetMapping("/billable-weight/{shipment}")
    public WarehouseReconciliationService.Charge billableWeight(@PathVariable String shipment) {
        return service.charge(shipment);
    }

    @GetMapping("/quality/{batch}")
    public WarehouseReconciliationService.Quality quality(@PathVariable String batch) {
        return service.quality(batch);
    }
}

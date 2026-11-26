package com.acme.operations.reconciliation;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("dev & !turno-cero")
@Transactional(readOnly = true)
class WarehouseReconciliationService {
    record Balance(long units) {}
    record Lot(String code, LocalDate expiresOn, int units) {}
    record Pick(String code, int units, long cumulativeUnits, long allocated) {}
    record SerialCheck(List<String> missing, List<String> unexpected) {}
    record Component(String component, int available, int requiredUnits, int kits) {}
    record KitPlan(int kits, List<Component> components) {}
    record Request(int position, String sku, int units) {}
    record Allocation(int position, String sku, int requested, int allocated) {}
    record AllocationPlan(List<Allocation> lines, Map<String, Integer> remaining) {}
    record Visit(String dock, String carrier) {}
    record DockVisit(String dock, List<String> carriers) {}
    record Parcel(String code, int actualKg, int volumetricKg) {}
    record Charge(int billableKg, List<Parcel> parcels) {}
    record Quality(boolean ready, long parcels, long passed) {}

    private final JdbcTemplate jdbc;

    WarehouseReconciliationService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    Balance balance(String sku, Instant cutoff) {
        var units = jdbc.queryForObject("""
                SELECT coalesce(sum(delta), 0)
                FROM warehouse_reconciliation.movement
                WHERE sku = ? AND effective_at <= ?
                """, Long.class, sku, Timestamp.from(cutoff));
        return new Balance(units);
    }

    List<Lot> eligibleLots(String sku, LocalDate departure, LocalDate arrival, int minimumDays) {
        LocalDate minimumExpiry = arrival.plusDays(minimumDays);
        return jdbc.query("""
                SELECT code, expires_on, units
                FROM warehouse_reconciliation.lot
                WHERE sku = ? AND expires_on >= ?
                ORDER BY expires_on, code
                """, (rs, row) -> new Lot(rs.getString(1), rs.getObject(2, LocalDate.class), rs.getInt(3)),
                sku, minimumExpiry);
    }

    List<Pick> fifo(String sku, int requested) {
        return jdbc.query("""
                WITH ordered AS (
                    SELECT code, units, priority,
                           sum(units) OVER (ORDER BY priority, code ROWS UNBOUNDED PRECEDING) AS cumulative
                    FROM warehouse_reconciliation.pick_lot WHERE sku = ?
                )
                SELECT code, units, cumulative, greatest(0, least(units, ? - (cumulative - units))) AS allocated
                FROM ordered ORDER BY priority, code
                """, (rs, row) -> new Pick(rs.getString(1), rs.getInt(2), rs.getLong(3), rs.getLong(4)),
                sku, requested);
    }

    SerialCheck serialCheck(String manifest) {
        var missing = serialDifference(manifest, "EXPECTED", "SCANNED");
        var unexpected = serialDifference(manifest, "SCANNED", "EXPECTED");
        return new SerialCheck(missing, unexpected);
    }

    private List<String> serialDifference(String manifest, String left, String right) {
        return jdbc.queryForList("""
                SELECT serial FROM warehouse_reconciliation.serial WHERE manifest = ? AND source = ?
                EXCEPT
                SELECT serial FROM warehouse_reconciliation.serial WHERE manifest = ? AND source = ?
                ORDER BY serial
                """, String.class, manifest, left, manifest, right);
    }

    KitPlan kitPlan(String kit) {
        var components = jdbc.query("""
                SELECT component, on_hand - reserved AS available, required_units
                FROM warehouse_reconciliation.kit_component WHERE kit = ? ORDER BY component
                """, (rs, row) -> {
                    int available = rs.getInt(2);
                    int required = rs.getInt(3);
                    return new Component(rs.getString(1), available, required, available / required);
                }, kit);
        int kits = components.stream().mapToInt(Component::kits).min().orElse(0);
        return new KitPlan(kits, components);
    }

    AllocationPlan allocate(String batch) {
        var startingStock = new LinkedHashMap<String, Integer>();
        jdbc.query("""
                SELECT sku, units FROM warehouse_reconciliation.preparation_stock
                WHERE batch = ? ORDER BY sku
                """, rs -> { startingStock.put(rs.getString(1), rs.getInt(2)); }, batch);
        var remaining = new LinkedHashMap<>(startingStock);
        var requests = jdbc.query("""
                SELECT position, sku, units FROM warehouse_reconciliation.preparation_request
                WHERE batch = ? ORDER BY position
                """, (rs, row) -> new Request(rs.getInt(1), rs.getString(2), rs.getInt(3)), batch);
        var allocations = new ArrayList<Allocation>();
        for (var request : requests) {
            int available = remaining.getOrDefault(request.sku(), 0);
            int allocated = Math.min(request.units(), available);
            remaining.merge(request.sku(), -allocated, Integer::sum);
            allocations.add(new Allocation(request.position(), request.sku(), request.units(), allocated));
        }
        return new AllocationPlan(allocations, remaining);
    }

    List<DockVisit> dockVisits(String batch) {
        var visits = jdbc.query("""
                SELECT dock, carrier FROM warehouse_reconciliation.carrier_visit
                WHERE batch = ? ORDER BY dock, carrier
                """, (rs, row) -> new Visit(rs.getString(1), rs.getString(2)), batch);
        var byDock = new LinkedHashMap<String, List<String>>();
        var carriers = new ArrayList<String>();
        for (var visit : visits) {
            byDock.computeIfAbsent(visit.dock(), ignored -> new ArrayList<>())
                    .add(visit.carrier());
        }
        return byDock.entrySet().stream()
                .map(entry -> new DockVisit(entry.getKey(), List.copyOf(entry.getValue())))
                .toList();
    }

    List<String> blockedBy(String code) {
        return jdbc.queryForList("""
            WITH RECURSIVE prerequisites(code) AS (
                SELECT d.prerequisite
                FROM warehouse_reconciliation.task_dependency d
                WHERE d.task = ?

                UNION

                SELECT d.prerequisite
                FROM warehouse_reconciliation.task_dependency d
                JOIN prerequisites p ON d.task = p.code
            )
            SELECT t.code
            FROM prerequisites p
            JOIN warehouse_reconciliation.task t ON t.code = p.code
            WHERE t.state <> 'DONE'
            ORDER BY t.code
            """, String.class, code);
    }


    Charge charge(String shipment) {
        var parcels = jdbc.query("""
            SELECT code, actual_kg, volumetric_kg
            FROM warehouse_reconciliation.parcel
            WHERE shipment = ?
            ORDER BY code
            """, (rs, row) -> new Parcel(
                rs.getString(1), rs.getInt(2), rs.getInt(3)), shipment);

        int billableKg = parcels.stream()
                .mapToInt(parcel -> Math.max(parcel.actualKg(), parcel.volumetricKg()))
                .sum();

        return new Charge(billableKg, parcels);
    }

    Quality quality(String batch) {
        return jdbc.queryForObject("""
                SELECT coalesce(bool_or(sealed AND weighed), false),
                       count(*), count(*) FILTER (WHERE sealed AND weighed)
                FROM warehouse_reconciliation.quality_control WHERE batch = ?
                """, (rs, row) -> new Quality(rs.getBoolean(1), rs.getLong(2), rs.getLong(3)), batch);
    }
}

package com.acme.operations.reconciliation;

import io.swagger.v3.oas.annotations.tags.Tag;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("dev & !turno-cero")
@PreAuthorize("hasAnyRole('ADMIN','OPERATIONS')")
@Tag(name = "Controles de almacén")
@RequestMapping("/api/v1/warehouse-controls")
class WarehouseDailyController {
    record Weight(long grossKg, int tareKg, long netKg) {}
    record Route(String prefix, String destination) {}
    record Discrepancy(int expected, int counted, BigDecimal discrepancyPercent) {}

    private final JdbcTemplate jdbc;

    WarehouseDailyController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/pallets/{code}/weight")
    public ResponseEntity<Weight> weight(@PathVariable String code) {
        var result = jdbc.query("""
                SELECT coalesce(sum(p.gross_kg), 0), c.tare_kg,
                       coalesce(sum(p.gross_kg - c.tare_kg), 0)
                FROM warehouse_daily.pallet c
                LEFT JOIN warehouse_daily.pallet_parcel p ON p.pallet = c.code
                WHERE c.code = ?
                GROUP BY c.code, c.tare_kg
                """, (rs, row) -> new Weight(rs.getLong(1), rs.getInt(2), rs.getLong(3)), code);
        return ResponseEntity.of(result.stream().findFirst());
    }

    @GetMapping("/routing/{label}")
    public ResponseEntity<Route> route(@PathVariable String label) {
        var rule = jdbc.query("""
                SELECT prefix, destination FROM warehouse_daily.route
                WHERE ? = prefix OR starts_with(?, prefix || '-')
                ORDER BY length(prefix), prefix LIMIT 1
                """, (rs, row) -> new Route(rs.getString(1), rs.getString(2)), label, label);
        return ResponseEntity.of(rule.stream().findFirst());
    }

    @GetMapping("/counts/{code}/discrepancy")
    public ResponseEntity<Discrepancy> discrepancy(@PathVariable String code) {
        var result = jdbc.query("""
                SELECT expected, counted FROM warehouse_daily.stock_count WHERE code = ?
                """, (rs, row) -> {
                    int expected = rs.getInt("expected");
                    int counted = rs.getInt("counted");
                    BigDecimal percent = counted == 0 ? null
                            : BigDecimal.valueOf(100L * Math.abs((long) expected - counted))
                                    .divide(BigDecimal.valueOf(counted), 2, RoundingMode.HALF_UP);
                    return new Discrepancy(expected, counted, percent);
                }, code);
        return ResponseEntity.of(result.stream().findFirst());
    }
}

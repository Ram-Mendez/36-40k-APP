package com.acme.operations.warehouse;

import com.acme.operations.common.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("dev & !turno-cero")
class TransferPracticeService {
    private final JdbcTemplate jdbc;
    TransferPracticeService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    TransferPracticeDtos.Transfer transfer(String code) {
        return jdbc.query("""
                        SELECT code, external_reference, revision, status
                        FROM transfer_practice.transfer WHERE code = ?
                        """,
                (rs, row) -> new TransferPracticeDtos.Transfer(rs.getString("code"),
                        rs.getString("external_reference"), rs.getLong("revision"), rs.getString("status")),
                code).stream().findFirst().orElseThrow(() -> missing("Traslado no encontrado"));
    }

    @Transactional
    public TransferPracticeDtos.Transfer patch(String code, JsonNode patch) {
        if (!patch.isObject() || patch.size() > (patch.has("externalReference") ? 1 : 0)) {
            throw invalid("El único campo editable es externalReference");
        }
        var reference = patch.get("externalReference");
        if (reference != null && !reference.isNull()
                && (!reference.isTextual() || reference.asText().length() > 120)) {
            throw invalid("externalReference admite texto de hasta 120 caracteres o null");
        }
        if (patch.hasNonNull("externalReference")) {
            jdbc.update("""
                    UPDATE transfer_practice.transfer
                    SET external_reference = ?, revision = revision + 1
                    WHERE code = ? AND external_reference IS DISTINCT FROM ?
                    """, reference.textValue(), code, reference.textValue());
        }
        return transfer(code);
    }

    @Transactional
    public void inspect(String code, boolean passed) {
        transfer(code);
        if (!passed) {
            recordRejectedInspection(code);
            throw conflict("INSPECTION_REJECTED", "La inspección rechazó el traslado");
        }
        jdbc.update("""
                INSERT INTO transfer_practice.inspection_attempt(transfer_code, result)
                VALUES (?, 'ACCEPTED')
                """, code);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordRejectedInspection(String code) {
        jdbc.update("""
                INSERT INTO transfer_practice.inspection_attempt(transfer_code, result)
                VALUES (?, 'REJECTED')
                """, code);
    }

    List<TransferPracticeDtos.Attempt> attempts(String code) {
        transfer(code);
        return jdbc.query("""
                SELECT id, result FROM transfer_practice.inspection_attempt
                WHERE transfer_code = ? ORDER BY id
                """, (rs, row) -> new TransferPracticeDtos.Attempt(rs.getLong(1), rs.getString(2)), code);
    }

    @Transactional
    public TransferPracticeDtos.Transfer dispatch(String code) {
        int changed = jdbc.update("""
                UPDATE transfer_practice.transfer SET status = 'DISPATCHED', revision = revision + 1
                WHERE code = ? AND status = 'DRAFT'
                """, code);
        var transfer = transfer(code);
        if (changed == 0) {
            throw conflict("TRANSFER_ALREADY_DISPATCHED", "El traslado ya salió");
        }
        return transfer;
    }

    List<TransferPracticeDtos.Event> dispatchEvents(String code) {
        transfer(code);
        return jdbc.query("""
                SELECT transfer_code, event_type FROM transfer_practice.dispatch_outbox
                WHERE transfer_code = ?
                """, (rs, row) -> new TransferPracticeDtos.Event(rs.getString(1), rs.getString(2)), code);
    }

    void recordDamage(TransferPracticeDtos.Damage damage) {
        jdbc.update("""
                INSERT INTO transfer_practice.damage_balance(sku, reason_code, units)
                VALUES (?, ?, ?)
                ON CONFLICT (sku, reason_code) DO UPDATE
                SET units = damage_balance.units + EXCLUDED.units
                """, damage.sku(), damage.reasonCode(), damage.units());
    }

    List<TransferPracticeDtos.DamageBalance> damageBalances(String sku) {
        return jdbc.query("""
                SELECT sku, reason_code, units FROM transfer_practice.damage_balance
                WHERE sku = ? ORDER BY id
                """, (rs, row) -> new TransferPracticeDtos.DamageBalance(rs.getString(1),
                        rs.getString(2), rs.getLong(3)), sku);
    }

    @Transactional
    public TransferPracticeDtos.Slot book(String dock, Instant start, Instant end) {
        if (!end.isAfter(start)) {
            throw invalid("endsAt debe ser posterior a startsAt");
        }
        var startAt = Timestamp.from(start);
        var endAt = Timestamp.from(end);
        jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))",
                (rs, row) -> 0, "transfer_practice:" + dock);
        boolean occupied = Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (
                    SELECT 1 FROM transfer_practice.dock_booking WHERE dock = ?
                    AND ((starts_at >= ? AND starts_at < ?) OR (ends_at > ? AND ends_at <= ?))
                )
                """, Boolean.class, dock, startAt, endAt, startAt, endAt));
        if (occupied) {
            throw conflict("DOCK_OCCUPIED", "El muelle ya está reservado en ese intervalo");
        }
        long id = jdbc.queryForObject("""
                INSERT INTO transfer_practice.dock_booking(dock, starts_at, ends_at)
                VALUES (?, ?, ?) RETURNING id
                """, Long.class, dock, startAt, endAt);
        return new TransferPracticeDtos.Slot(id, dock, start, end);
    }

    @Transactional
    public TransferPracticeDtos.Job claimNext(String queue) {
        jdbc.execute("SET LOCAL lock_timeout = '750ms'");
        var jobs = jdbc.query("""
                SELECT position FROM transfer_practice.picking_job
                WHERE queue = ? AND status = 'READY'
                ORDER BY position LIMIT 1 FOR UPDATE
                """, (rs, row) -> rs.getInt(1), queue);
        if (jobs.isEmpty()) {
            throw conflict("NO_READY_JOB", "No hay trabajo listo en la cola");
        }
        int position = jobs.getFirst();
        jdbc.update("""
                UPDATE transfer_practice.picking_job SET status = 'CLAIMED'
                WHERE queue = ? AND position = ?
                """, queue, position);
        return new TransferPracticeDtos.Job(position, "CLAIMED");
    }

    @Transactional
    public TransferPracticeDtos.Weight confirmWeight(String code, BigDecimal kilograms) {
        var weights = jdbc.query("""
                SELECT expected_weight FROM transfer_practice.transfer WHERE code = ? FOR UPDATE
                """, (rs, row) -> rs.getBigDecimal(1), code);
        if (weights.isEmpty()) {
            throw missing("Traslado no encontrado");
        }
        if (!weights.getFirst().equals(kilograms)) {
            throw conflict("WEIGHT_MISMATCH", "El peso no coincide con lo previsto");
        }
        jdbc.update("""
                UPDATE transfer_practice.transfer SET confirmed_weight = ? WHERE code = ?
                """, kilograms, code);
        return new TransferPracticeDtos.Weight(kilograms);
    }

    TransferPracticeDtos.LotCount count(String lot) {
        return jdbc.query("""
                        SELECT l.lot, l.expected_units, c.counted_units
                        FROM transfer_practice.expected_lot l
                        LEFT JOIN transfer_practice.lot_count c ON c.lot = l.lot WHERE l.lot = ?
                        """, (rs, row) -> new TransferPracticeDtos.LotCount(rs.getString("lot"),
                        rs.getInt("expected_units"), rs.getInt("counted_units")), lot).stream()
                .findFirst().orElseThrow(() -> missing("Lote no encontrado"));
    }

    private static BusinessException conflict(String code, String message) {
        return new BusinessException(HttpStatus.CONFLICT, code, message);
    }

    private static BusinessException invalid(String message) {
        return new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_TRANSFER_REQUEST", message);
    }

    private static BusinessException missing(String message) {
        return new BusinessException(HttpStatus.NOT_FOUND, "TRANSFER_DATA_NOT_FOUND", message);
    }
}

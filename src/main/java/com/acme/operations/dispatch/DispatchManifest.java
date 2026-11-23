package com.acme.operations.dispatch;

import com.acme.operations.common.BusinessException;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Service
@Profile("turno-cero")
class DispatchManifest {
    record Status(boolean sealed, boolean receipt, boolean released) {}
    record Sealed(UUID attemptId) {}

    static class SealRejected extends Exception {
        SealRejected() {
            super("The scanner reports a broken seal");
        }
    }

    static class LoadingBlocked extends RuntimeException {
        LoadingBlocked() {
            super("The gate requires a verified seal and a printed receipt");
        }
    }

    private final JdbcTemplate jdbc;
    private final ApplicationEventPublisher events;

    DispatchManifest(JdbcTemplate jdbc, ApplicationEventPublisher events) {
        this.jdbc = jdbc;
        this.events = events;
    }

    @Transactional
    public UUID seal(UUID attemptId, boolean intact) throws SealRejected {
        int inserted = jdbc.update("""
                INSERT INTO dispatch_seal(attempt_id, verified) VALUES (?, ?)
                ON CONFLICT (attempt_id) DO NOTHING
                """, attemptId, intact);
        if (inserted == 0) {
            throw new BusinessException(HttpStatus.CONFLICT,
                    "ATTEMPT_EXISTS", "Create a new seal attempt");
        }
        if (!intact) {
            throw new SealRejected();
        }
        events.publishEvent(new Sealed(attemptId));
        return attemptId;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_ROLLBACK)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void printReceipt(Sealed event) {
        jdbc.update("INSERT INTO dispatch_receipt(attempt_id) VALUES (?)", event.attemptId());
    }

    Status status(UUID attemptId) {
        return jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM dispatch_seal WHERE attempt_id=?) AS sealed,
                       EXISTS (SELECT 1 FROM dispatch_receipt WHERE attempt_id=?) AS receipt,
                       EXISTS (SELECT 1 FROM dispatch_release WHERE attempt_id=?) AS released
                """, (rs, row) -> new Status(rs.getBoolean("sealed"), rs.getBoolean("receipt"),
                        rs.getBoolean("released")), attemptId, attemptId, attemptId);
    }

    @Transactional
    public Status release(UUID attemptId) {
        int inserted = jdbc.update("""
                INSERT INTO dispatch_release(attempt_id)
                SELECT s.attempt_id FROM dispatch_seal s JOIN dispatch_receipt r USING (attempt_id)
                WHERE s.attempt_id=? AND s.verified
                ON CONFLICT (attempt_id) DO NOTHING
                """, attemptId);
        Status status = status(attemptId);
        if (inserted == 0 && !status.released()) {
            throw new LoadingBlocked();
        }
        return status;
    }
}

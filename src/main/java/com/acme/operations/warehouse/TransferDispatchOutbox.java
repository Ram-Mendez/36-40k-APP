package com.acme.operations.warehouse;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@Profile("dev & !turno-cero")
class TransferDispatchOutbox {
    record Dispatched(String code) {}

    private final JdbcTemplate jdbc;

    TransferDispatchOutbox(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @TransactionalEventListener
    public void enqueue(Dispatched event) {
        jdbc.update("""
                INSERT INTO transfer_practice.dispatch_outbox(transfer_code, event_type)
                VALUES (?, 'TRANSFER_DISPATCHED') ON CONFLICT DO NOTHING
                """, event.code());
    }
}

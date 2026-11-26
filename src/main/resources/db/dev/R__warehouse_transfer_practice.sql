CREATE SCHEMA IF NOT EXISTS transfer_practice;

CREATE TABLE IF NOT EXISTS transfer_practice.transfer (
    code text PRIMARY KEY,
    external_reference text,
    revision bigint NOT NULL DEFAULT 0,
    status text NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'DISPATCHED')),
    expected_weight numeric(12,3) NOT NULL DEFAULT 12.500,
    confirmed_weight numeric(12,3)
);

CREATE TABLE IF NOT EXISTS transfer_practice.inspection_attempt (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    transfer_code text NOT NULL REFERENCES transfer_practice.transfer(code),
    result text NOT NULL
);

CREATE TABLE IF NOT EXISTS transfer_practice.dispatch_outbox (
    transfer_code text PRIMARY KEY REFERENCES transfer_practice.transfer(code),
    event_type text NOT NULL
);

CREATE TABLE IF NOT EXISTS transfer_practice.damage_balance (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    sku text NOT NULL,
    reason_code text,
    units bigint NOT NULL CHECK (units > 0),
    UNIQUE (sku, reason_code)
);

CREATE TABLE IF NOT EXISTS transfer_practice.dock_booking (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    dock text NOT NULL,
    starts_at timestamptz NOT NULL,
    ends_at timestamptz NOT NULL,
    CHECK (starts_at < ends_at),
    UNIQUE (dock, starts_at, ends_at)
);

CREATE TABLE IF NOT EXISTS transfer_practice.picking_job (
    queue text NOT NULL,
    position integer NOT NULL,
    status text NOT NULL DEFAULT 'READY' CHECK (status IN ('READY', 'CLAIMED')),
    PRIMARY KEY (queue, position)
);

CREATE TABLE IF NOT EXISTS transfer_practice.expected_lot (
    lot text PRIMARY KEY,
    expected_units integer NOT NULL
);

CREATE TABLE IF NOT EXISTS transfer_practice.lot_count (
    lot text PRIMARY KEY REFERENCES transfer_practice.expected_lot(lot),
    counted_units integer NOT NULL
);

INSERT INTO transfer_practice.transfer(code, external_reference)
SELECT code, 'ALB-ORIGINAL' FROM unnest(ARRAY[
    'TR-25', 'TR-25-B', 'TR-26', 'TR-26-B', 'TR-26-C', 'TR-29', 'TR-31', 'TR-31-B', 'TR-33',
    'QA-25', 'QA-26', 'QA-29', 'QA-31', 'QA-33'
]) AS v(code) ON CONFLICT DO NOTHING;

INSERT INTO transfer_practice.dock_booking(dock, starts_at, ends_at)
VALUES ('DOCK-28', '2026-12-02T09:00:00Z', '2026-12-02T12:00:00Z'),
       ('DOCK-28-B', '2026-12-02T09:00:00Z', '2026-12-02T12:00:00Z'),
       ('QA-28', '2026-12-02T09:00:00Z', '2026-12-02T12:00:00Z')
ON CONFLICT DO NOTHING;

INSERT INTO transfer_practice.picking_job(queue, position)
VALUES ('Q-30', 1), ('Q-30', 2), ('Q-30-B', 1), ('Q-30-B', 2),
       ('QA-30', 1), ('QA-30', 2)
ON CONFLICT DO NOTHING;

INSERT INTO transfer_practice.expected_lot(lot, expected_units)
VALUES ('LOT-32-PENDING', 8), ('LOT-32-EMPTY', 8) ON CONFLICT DO NOTHING;

INSERT INTO transfer_practice.lot_count(lot, counted_units)
VALUES ('LOT-32-EMPTY', 0) ON CONFLICT DO NOTHING;

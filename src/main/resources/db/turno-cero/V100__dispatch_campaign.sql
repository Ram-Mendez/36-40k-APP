CREATE TABLE dispatch_dock (
    wave text NOT NULL,
    dock text NOT NULL,
    capacity integer NOT NULL CHECK (capacity > 0),
    PRIMARY KEY (wave, dock)
);

CREATE TABLE dispatch_label (
    barcode text PRIMARY KEY,
    product_id bigint NOT NULL REFERENCES product(id),
    wave text NOT NULL
);

CREATE TABLE dispatch_seal (
    attempt_id uuid PRIMARY KEY,
    verified boolean NOT NULL
);

CREATE TABLE dispatch_receipt (
    attempt_id uuid PRIMARY KEY REFERENCES dispatch_seal(attempt_id)
);

CREATE TABLE dispatch_release (
    attempt_id uuid PRIMARY KEY REFERENCES dispatch_receipt(attempt_id),
    released_at timestamptz NOT NULL DEFAULT now()
);

INSERT INTO dispatch_dock (wave, dock, capacity)
VALUES ('ECHO-17', 'A', 8), ('NORTH-02', 'A', 3);

INSERT INTO dispatch_label (barcode, product_id, wave)
SELECT '000417', id, 'ECHO-17' FROM product WHERE sku = 'GLOVE-100'
UNION ALL
SELECT '732', id, 'ECHO-17' FROM product WHERE sku = 'DRILL-18V';

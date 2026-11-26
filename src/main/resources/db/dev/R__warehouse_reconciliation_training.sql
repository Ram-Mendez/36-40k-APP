CREATE SCHEMA IF NOT EXISTS warehouse_reconciliation;

CREATE TABLE IF NOT EXISTS warehouse_reconciliation.movement (
    code text PRIMARY KEY, sku text NOT NULL, delta integer NOT NULL,
    effective_at timestamptz NOT NULL, entered_at timestamptz NOT NULL
);
INSERT INTO warehouse_reconciliation.movement VALUES
    ('M39-1','SKU-39',10,'2026-11-24T08:00:00Z','2026-11-24T08:00:00Z'),
    ('M39-2','SKU-39',-4,'2026-11-24T09:45:00Z','2026-11-24T11:00:00Z'),
    ('M39-3','SKU-39',8,'2026-11-24T10:15:00Z','2026-11-24T10:30:00Z')
ON CONFLICT DO NOTHING;

CREATE TABLE IF NOT EXISTS warehouse_reconciliation.lot (
    code text PRIMARY KEY, sku text NOT NULL, expires_on date NOT NULL, units integer NOT NULL
);
INSERT INTO warehouse_reconciliation.lot VALUES
    ('L40-A','SKU-40','2026-12-06',4),('L40-B','SKU-40','2026-12-09',5),
    ('L40-C','SKU-40','2026-12-14',8)
ON CONFLICT DO NOTHING;

CREATE TABLE IF NOT EXISTS warehouse_reconciliation.pick_lot (
    code text PRIMARY KEY, sku text NOT NULL, priority integer NOT NULL, units integer NOT NULL
);
INSERT INTO warehouse_reconciliation.pick_lot VALUES
    ('L41-A','SKU-41',1,4),('L41-B','SKU-41',2,5),('L41-C','SKU-41',3,6)
ON CONFLICT DO NOTHING;

CREATE TABLE IF NOT EXISTS warehouse_reconciliation.serial (
    manifest text NOT NULL, source text NOT NULL, serial text NOT NULL,
    PRIMARY KEY (manifest, source, serial)
);
INSERT INTO warehouse_reconciliation.serial VALUES
    ('MF-42','EXPECTED','S1'),('MF-42','EXPECTED','S2'),
    ('MF-42','SCANNED','S1'),('MF-42','SCANNED','S3')
ON CONFLICT DO NOTHING;

CREATE TABLE IF NOT EXISTS warehouse_reconciliation.kit_component (
    kit text NOT NULL, component text NOT NULL, required_units integer NOT NULL,
    on_hand integer NOT NULL, reserved integer NOT NULL,
    PRIMARY KEY (kit, component)
);
INSERT INTO warehouse_reconciliation.kit_component VALUES
    ('KIT-43','BASE',2,12,2),('KIT-43','WHEEL',4,15,3),('KIT-43','HANDLE',1,8,0)
ON CONFLICT DO NOTHING;

CREATE TABLE IF NOT EXISTS warehouse_reconciliation.preparation_request (
    batch text NOT NULL, position integer NOT NULL, sku text NOT NULL, units integer NOT NULL,
    PRIMARY KEY (batch, position)
);
INSERT INTO warehouse_reconciliation.preparation_request VALUES
    ('PICK-44',1,'BOLT',6),('PICK-44',2,'BOLT',6),('PICK-44',3,'TAPE',3)
ON CONFLICT DO NOTHING;

CREATE TABLE IF NOT EXISTS warehouse_reconciliation.preparation_stock (
    batch text NOT NULL, sku text NOT NULL, units integer NOT NULL, PRIMARY KEY (batch,sku)
);
INSERT INTO warehouse_reconciliation.preparation_stock VALUES
    ('PICK-44','BOLT',10),('PICK-44','TAPE',5)
ON CONFLICT DO NOTHING;

CREATE TABLE IF NOT EXISTS warehouse_reconciliation.carrier_visit (
    batch text NOT NULL, dock text NOT NULL, carrier text NOT NULL,
    PRIMARY KEY (batch,dock,carrier)
);
INSERT INTO warehouse_reconciliation.carrier_visit VALUES
    ('VIS-45','A','NORTE'),('VIS-45','A','SUR'),('VIS-45','B','CENTRO')
ON CONFLICT DO NOTHING;

CREATE TABLE IF NOT EXISTS warehouse_reconciliation.task (
    code text PRIMARY KEY, state text NOT NULL
);
INSERT INTO warehouse_reconciliation.task VALUES ('LOAD-46','READY'),('PACK-46','DONE'),
    ('CHECK-46','PENDING'),('LABEL-46','DONE')
ON CONFLICT DO NOTHING;

CREATE TABLE IF NOT EXISTS warehouse_reconciliation.task_dependency (
    task text REFERENCES warehouse_reconciliation.task(code),
    prerequisite text REFERENCES warehouse_reconciliation.task(code), PRIMARY KEY (task,prerequisite)
);
INSERT INTO warehouse_reconciliation.task_dependency VALUES
    ('LOAD-46','PACK-46'),('LOAD-46','LABEL-46'),('PACK-46','CHECK-46')
ON CONFLICT DO NOTHING;

CREATE TABLE IF NOT EXISTS warehouse_reconciliation.parcel (
    shipment text NOT NULL, code text NOT NULL, actual_kg integer NOT NULL,
    volumetric_kg integer NOT NULL, PRIMARY KEY (shipment, code)
);
INSERT INTO warehouse_reconciliation.parcel VALUES ('SHIP-47','P1',8,3),('SHIP-47','P2',2,6)
ON CONFLICT DO NOTHING;

CREATE TABLE IF NOT EXISTS warehouse_reconciliation.quality_control (
    batch text NOT NULL, parcel text NOT NULL, sealed boolean NOT NULL, weighed boolean NOT NULL,
    PRIMARY KEY (batch,parcel)
);
INSERT INTO warehouse_reconciliation.quality_control VALUES
    ('QC-48','P1',true,true),('QC-48','P2',true,false),('QC-48-OK','P1',true,true)
ON CONFLICT DO NOTHING;

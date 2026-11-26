CREATE SCHEMA IF NOT EXISTS warehouse_daily;

CREATE TABLE IF NOT EXISTS warehouse_daily.pallet (
    code text PRIMARY KEY, tare_kg integer NOT NULL CHECK (tare_kg >= 0)
);
CREATE TABLE IF NOT EXISTS warehouse_daily.pallet_parcel (
    pallet text REFERENCES warehouse_daily.pallet(code),
    parcel text NOT NULL,
    gross_kg integer NOT NULL CHECK (gross_kg >= 0),
    PRIMARY KEY (pallet, parcel)
);
INSERT INTO warehouse_daily.pallet VALUES ('PAL-49',12),('PAL-49-SINGLE',12)
ON CONFLICT DO NOTHING;
INSERT INTO warehouse_daily.pallet_parcel VALUES
    ('PAL-49','BOX-1',50),('PAL-49','BOX-2',40),('PAL-49-SINGLE','BOX-1',50)
ON CONFLICT DO NOTHING;

CREATE TABLE IF NOT EXISTS warehouse_daily.route (
    prefix text PRIMARY KEY, destination text NOT NULL
);
INSERT INTO warehouse_daily.route VALUES ('R','GENERAL'),('R-04','BAY-04'),('R-04-C','COLD-BAY-04')
ON CONFLICT DO NOTHING;

CREATE TABLE IF NOT EXISTS warehouse_daily.stock_count (
    code text PRIMARY KEY, expected integer NOT NULL CHECK (expected >= 0),
    counted integer NOT NULL CHECK (counted >= 0)
);
INSERT INTO warehouse_daily.stock_count VALUES ('CNT-51',120,90),('CNT-51-OVER',120,150),('CNT-51-ZERO',0,12)
ON CONFLICT DO NOTHING;

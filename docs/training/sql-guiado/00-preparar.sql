-- Selecciona el archivo completo y ejecútalo en la consola de lab_20261117.
-- Crea el material de práctica. Puedes volver a ejecutarlo en la misma base.
BEGIN;

CREATE SCHEMA IF NOT EXISTS sql_lab10;

CREATE TABLE IF NOT EXISTS sql_lab10.clientes (
    id integer PRIMARY KEY,
    nombre text NOT NULL
);
CREATE TABLE IF NOT EXISTS sql_lab10.productos (
    id integer PRIMARY KEY,
    nombre text NOT NULL
);
CREATE TABLE IF NOT EXISTS sql_lab10.pedidos (
    id integer PRIMARY KEY,
    cliente_id integer NOT NULL REFERENCES sql_lab10.clientes(id),
    estado text NOT NULL,
    total numeric(10, 2) NOT NULL,
    creado date NOT NULL
);
CREATE TABLE IF NOT EXISTS sql_lab10.lineas (
    pedido_id integer REFERENCES sql_lab10.pedidos(id),
    producto_id integer REFERENCES sql_lab10.productos(id),
    unidades integer NOT NULL CHECK (unidades > 0),
    PRIMARY KEY (pedido_id, producto_id)
);
CREATE TABLE IF NOT EXISTS sql_lab10.stock (
    producto_id integer PRIMARY KEY REFERENCES sql_lab10.productos(id),
    fisicas integer NOT NULL,
    reservadas integer NOT NULL CHECK (reservadas BETWEEN 0 AND fisicas)
);
CREATE TABLE IF NOT EXISTS sql_lab10.caja (
    caso integer NOT NULL,
    id integer NOT NULL,
    saldo integer NOT NULL CHECK (saldo >= 0),
    PRIMARY KEY (caso, id)
);
CREATE TABLE IF NOT EXISTS sql_lab10.envios (
    caso integer NOT NULL,
    id integer NOT NULL,
    estado text NOT NULL CHECK (estado IN ('PENDIENTE', 'LISTO', 'ENVIADO')),
    PRIMARY KEY (caso, id)
);

INSERT INTO sql_lab10.clientes (id, nombre) VALUES
    (1, 'Ana'), (2, 'Luis'), (3, 'Eva'), (4, 'Leo')
ON CONFLICT (id) DO NOTHING;
INSERT INTO sql_lab10.productos (id, nombre) VALUES
    (11, 'Guantes'), (12, 'Cajas'), (13, 'Cinta'), (14, 'Bolsas')
ON CONFLICT (id) DO NOTHING;
INSERT INTO sql_lab10.pedidos (id, cliente_id, estado, total, creado) VALUES
    (101, 1, 'COMPLETED', 30, DATE '2026-11-01'),
    (102, 1, 'COMPLETED', 30, DATE '2026-11-02'),
    (103, 2, 'CONFIRMED', 20, DATE '2026-11-02'),
    (104, 2, 'COMPLETED', 20, DATE '2026-11-02'),
    (105, 4, 'DRAFT',     10, DATE '2026-11-02')
ON CONFLICT (id) DO NOTHING;
INSERT INTO sql_lab10.lineas (pedido_id, producto_id, unidades) VALUES
    (101, 11, 1), (101, 12, 1), (102, 11, 2),
    (103, 12, 1), (104, 12, 1), (105, 13, 1)
ON CONFLICT (pedido_id, producto_id) DO NOTHING;
INSERT INTO sql_lab10.stock (producto_id, fisicas, reservadas) VALUES
    (11, 10, 7), (12, 4, 0), (13, 5, 0)
ON CONFLICT (producto_id) DO NOTHING;

COMMIT;

SELECT current_database() AS base,
       (SELECT count(*) FROM sql_lab10.clientes) AS clientes,
       (SELECT count(*) FROM sql_lab10.pedidos) AS pedidos,
       (SELECT count(*) FROM sql_lab10.lineas) AS lineas;

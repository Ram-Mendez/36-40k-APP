-- Ejecuta el archivo completo en lab_20261117 para instalar esta tanda.
BEGIN;
CREATE SCHEMA IF NOT EXISTS sql_lab20;

CREATE TABLE IF NOT EXISTS sql_lab20.expediciones (
    id integer PRIMARY KEY,
    centro text NOT NULL,
    estado text NOT NULL,
    dia date NOT NULL,
    sku text NOT NULL,
    solicitadas integer NOT NULL CHECK (solicitadas > 0),
    servidas integer NOT NULL CHECK (servidas BETWEEN 0 AND solicitadas),
    bruto numeric(10,2) NOT NULL,
    descuento numeric(10,2)
);
INSERT INTO sql_lab20.expediciones VALUES
    (401, 'NORTE', 'LISTA',    '2024-12-30', 'CAJA',  3, 2, 90, NULL),
    (402, 'SUR',   'LISTA',    '2024-12-31', 'CAJA',  4, 4, 80, 5),
    (403, 'NORTE', 'URGENTE',  '2025-01-02', 'CAJA',  5, 0, 60, 0),
    (404, 'SUR',   'URGENTE',  '2025-01-05', 'CINTA', 2, 1, 20, NULL),
    (405, 'NORTE', 'CERRADA',  '2025-01-06', 'CINTA', 4, 4, 40, 2),
    (406, 'NORTE', 'LISTA',    '2025-12-29', 'CAJA',  1, 1, 12, 1)
ON CONFLICT (id) DO NOTHING;

CREATE TABLE IF NOT EXISTS sql_lab20.movimientos (
    id integer PRIMARY KEY,
    canal text NOT NULL,
    sku text NOT NULL,
    unidades integer NOT NULL
);
-- El id crece en el orden en que se registra cada movimiento.
INSERT INTO sql_lab20.movimientos VALUES
    (501, 'TIENDA', 'CAJA',   8),
    (502, 'WEB',    'CINTA', 10),
    (503, 'WEB',    'CAJA',   8),
    (504, 'TIENDA', 'CINTA', -2),
    (505, 'WEB',    'CAJA',  -3)
ON CONFLICT (id) DO NOTHING;

CREATE TABLE IF NOT EXISTS sql_lab20.tarifas (
    sku text NOT NULL,
    desde date NOT NULL,
    euros numeric(10,2) NOT NULL,
    PRIMARY KEY (sku, desde)
);
INSERT INTO sql_lab20.tarifas VALUES
    ('CAJA', '2024-01-01', 8), ('CAJA', '2025-01-02', 10),
    ('CAJA', '2025-06-01', 9), ('CINTA', '2024-01-01', 3)
ON CONFLICT (sku, desde) DO NOTHING;

CREATE TABLE IF NOT EXISTS sql_lab20.lotes (
    id integer PRIMARY KEY,
    sku text NOT NULL,
    unidades integer NOT NULL CHECK (unidades > 0),
    coste_unitario numeric(10,2) NOT NULL
);
INSERT INTO sql_lab20.lotes VALUES
    (601, 'CAJA', 2, 10), (602, 'CAJA', 8, 5),
    (603, 'CINTA', 4, 2), (604, 'CINTA', 4, 4)
ON CONFLICT (id) DO NOTHING;

CREATE TABLE IF NOT EXISTS sql_lab20.turnos (
    centro text NOT NULL,
    persona text NOT NULL,
    bultos integer NOT NULL,
    PRIMARY KEY (centro, persona)
);
INSERT INTO sql_lab20.turnos VALUES
    ('NORTE', 'Ana', 12), ('NORTE', 'Leo', 12),
    ('NORTE', 'Mia', 10), ('NORTE', 'Ivo', 8),
    ('SUR', 'Sol', 11), ('SUR', 'Paz', 9), ('SUR', 'Luz', 7)
ON CONFLICT (centro, persona) DO NOTHING;

CREATE TABLE IF NOT EXISTS sql_lab20.zonas (
    id integer PRIMARY KEY,
    padre_id integer REFERENCES sql_lab20.zonas(id),
    nombre text NOT NULL,
    habilitada boolean NOT NULL
);
INSERT INTO sql_lab20.zonas VALUES
    (1, NULL, 'Almacen norte', true), (2, 1, 'Pasillo A', true),
    (3, 1, 'Pasillo B', false), (4, 2, 'Estante A1', true),
    (5, 3, 'Estante B1', true), (6, 3, 'Estante B2', false),
    (7, NULL, 'Almacen sur', true), (8, 7, 'Pasillo S', true)
ON CONFLICT (id) DO NOTHING;
COMMIT;

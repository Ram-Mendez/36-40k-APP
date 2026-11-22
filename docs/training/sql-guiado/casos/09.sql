-- Console A. 1. Reset with two shipments.
INSERT INTO sql_lab10.envios (caso, id, estado)
VALUES (9, 1, 'PENDIENTE'), (9, 2, 'PENDIENTE')
ON CONFLICT (caso, id) DO UPDATE SET estado = EXCLUDED.estado;

-- A2. Operator A opens shipment 1 to prepare it.
-- Select through the semicolon after FOR UPDATE and pause here.
BEGIN;
SET LOCAL idle_in_transaction_session_timeout = '2min';
SET LOCAL lock_timeout = '2s';
SELECT id, estado FROM sql_lab10.envios
WHERE caso = 9
ORDER BY id
FOR UPDATE;

-- A3. Release the locks after finishing the checks in console B.
ROLLBACK;

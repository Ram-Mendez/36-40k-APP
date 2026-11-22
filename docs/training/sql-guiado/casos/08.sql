-- 1. Reset the exercise with two pending shipments.
INSERT INTO sql_lab10.envios (caso, id, estado)
VALUES (8, 1, 'PENDIENTE'), (8, 2, 'PENDIENTE')
ON CONFLICT (caso, id) DO UPDATE SET estado = EXCLUDED.estado;

-- 2. Shipment 1 has a valid label. The operator rejects shipment 2's label.
BEGIN;
SAVEPOINT antes_de_revisar;
UPDATE sql_lab10.envios SET estado = 'LISTO' WHERE caso = 8 AND id = 1;
UPDATE sql_lab10.envios SET estado = 'LISTO' WHERE caso = 8 AND id = 2;
ROLLBACK TO SAVEPOINT antes_de_revisar;
COMMIT;

-- 3. Check which shipments are ready after saving the batch.
SELECT id, estado FROM sql_lab10.envios WHERE caso = 8 ORDER BY id;

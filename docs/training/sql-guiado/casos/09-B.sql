-- Console B, Tx: Auto. Run each SELECT separately.
-- B1. Operator B tries to work on shipment 2.
SELECT id, estado FROM sql_lab10.envios
WHERE caso = 9 AND id = 2
FOR UPDATE NOWAIT;

-- B2. Control check. Try to open shipment 1, which A is preparing.
SELECT id, estado FROM sql_lab10.envios
WHERE caso = 9 AND id = 1
FOR UPDATE NOWAIT;

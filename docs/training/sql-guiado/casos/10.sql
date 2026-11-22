-- Console A. 1. Reset with 100 euros in the till.
INSERT INTO sql_lab10.caja (caso, id, saldo) VALUES (10, 1, 100)
ON CONFLICT (caso, id) DO UPDATE SET saldo = EXCLUDED.saldo;

-- A2. Simulate A reading the balance and storing it in a variable.
-- The temporary table exists only in A until COMMIT or ROLLBACK.
BEGIN ISOLATION LEVEL READ COMMITTED;
SET LOCAL idle_in_transaction_session_timeout = '2min';
SET LOCAL lock_timeout = '2s';
CREATE TEMP TABLE lectura_caja_10 ON COMMIT DROP AS
SELECT saldo AS saldo_leido FROM sql_lab10.caja WHERE caso = 10 AND id = 1;
SELECT saldo_leido FROM pg_temp.lectura_caja_10;

-- A3. After B's withdrawal, compare the first reading with the current balance.
SELECT lectura.saldo_leido, caja.saldo AS saldo_actual
FROM pg_temp.lectura_caja_10 AS lectura
CROSS JOIN sql_lab10.caja AS caja
WHERE caja.caso = 10 AND caja.id = 1;

-- A4. A withdraws 30 using the balance read earlier.
UPDATE sql_lab10.caja SET saldo = (SELECT saldo_leido FROM pg_temp.lectura_caja_10) - 30
WHERE caso = 10 AND id = 1;
COMMIT;

-- A5. Final balance.
SELECT id, saldo FROM sql_lab10.caja WHERE caso = 10 AND id = 1;

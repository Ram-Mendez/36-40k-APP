-- 1. Reset the exercise. Run this statement before each attempt.
INSERT INTO sql_lab10.caja (caso, id, saldo)
VALUES (7, 1, 100), (7, 2, 20)
ON CONFLICT (caso, id) DO UPDATE SET saldo = EXCLUDED.saldo;

-- 2. Transfer 30 from till 1 to till 2. The operator cancels at the end.
BEGIN;
UPDATE sql_lab10.caja SET saldo = saldo - 30 WHERE caso = 7 AND id = 1;
COMMIT;
BEGIN;
UPDATE sql_lab10.caja SET saldo = saldo + 30 WHERE caso = 7 AND id = 2;
ROLLBACK;

-- 3. Read the saved balances in till order.
SELECT id, saldo FROM sql_lab10.caja WHERE caso = 7 ORDER BY id;

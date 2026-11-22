-- Console B. Withdraw 20 euros and commit between blocks A2 and A3.
BEGIN;
SET LOCAL lock_timeout = '2s';
UPDATE sql_lab10.caja SET saldo = saldo - 20 WHERE caso = 10 AND id = 1
RETURNING id, saldo;
COMMIT;

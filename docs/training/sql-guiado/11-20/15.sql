SELECT sku, SUM(unidades) AS saldo
FROM sql_lab20.movimientos
GROUP BY sku, canal
ORDER BY sku, canal;

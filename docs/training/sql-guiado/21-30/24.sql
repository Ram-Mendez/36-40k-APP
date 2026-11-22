SELECT sku, SUM(unidades) AS saldo
FROM sql_lab20.movimientos
WHERE unidades < 9
GROUP BY sku
ORDER BY sku;

WITH diario AS (
    SELECT sku, unidades FROM sql_lab20.movimientos WHERE canal = 'TIENDA'
    UNION
    SELECT sku, unidades FROM sql_lab20.movimientos WHERE canal = 'WEB'
)
SELECT sku, count(*) AS movimientos, sum(unidades) AS saldo
FROM diario
GROUP BY sku
ORDER BY sku;

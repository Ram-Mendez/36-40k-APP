-- Práctica: cálculo de pagos en BillingRepository.
-- Saldo de la factura 501, sin devoluciones de productos.
SELECT i.id AS invoice_id,
       i.product_total + i.shipping_total AS total,
       SUM(e.amount) AS paid,
       i.product_total + i.shipping_total - SUM(e.amount) AS balance
FROM sql_backend_41_50.invoice AS i
JOIN sql_backend_41_50.invoice_entry AS e ON e.invoice_id = i.id
WHERE i.id = 501
GROUP BY i.id, i.product_total, i.shipping_total;

-- Práctica: BillingRepository.entry(invoiceId, entryId).
-- Valores que el fragmento Java de la guía pasa a los signos ?.
SELECT id, invoice_id, kind, amount
FROM sql_backend_41_50.invoice_entry
WHERE invoice_id = 1001 AND id = 501;

# INC-04 · Reenviar un pago idéntico devuelve conflicto

**Incidencia.** Una factura de 47,50 € ya tiene un pago de 12,50 €. El cliente vuelve a enviar el mismo pago porque no recibió la primera respuesta.

**Datos preparados:** factura `1`; un pago de 12,50 € ya registrado.

## Reproducir en Swagger

1. Consulta `GET /api/v1/billing/invoices/{id}/entries` con `id = 1`.
2. En `POST /api/v1/billing/invoices/{id}/payments`, pon `id = 1` y reenvía:

```json
{
  "requestId": "808f0e5f-e3f0-495e-b3d0-1eef2e8bf3ea",
  "amount": 12.5,
  "reference": "Transferencia L22"
}
```

3. Repite el envío con el mismo `requestId` y `amount = 12.51`. Consulta la factura y sus apuntes.

**Fallo observado:** ambos envíos responden `409 IDEMPOTENCY_CONFLICT`.

**Resultado esperado:** el pago idéntico devuelve `200` y el ID del apunte original. El pago cambia![img.png](img.png) qdo devuelve `409`. Sigue habiendo un apunte, 12,50 € cobrados y 35 € pendientes.

**Para investigar:** pon un breakpoint en [BillingController.pay](../../../src/main/java/com/acme/operations/billing/BillingController.java) y sigue la petición. Después de corregir, reinicia el backend y repite con los mismos datos.

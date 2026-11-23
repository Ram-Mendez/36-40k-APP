# INC-08 · Una factura antigua cambia al actualizar la dirección del cliente

**Incidencia.** Una factura se emitió con la dirección «Calle Olmo 4». El cliente cambia su dirección. Al volver a abrir la factura antigua aparece la dirección nueva.

**Datos preparados:** cliente `10`; factura antigua `3`; pedido completado y aún sin facturar `11`.

## Reproducir en Swagger

1. Consulta `GET /api/v1/billing/invoices/{id}` con `id = 3`. Anota `customerAddress`. 4
2. En `PUT /api/v1/customers/{id}`, pon `id = 10` y envía:

```json
{
  "name": "L22-3376881e66704148ba56",
  "taxId": "L22-3376881E66704148BA56",
  "email": "ventas@example.test",
  "version": 0,
  "address": "Calle Roble 9"
}
```

3. Consulta el cliente `10` y vuelve a leer la factura `3`.
4. En `POST /api/v1/billing/invoices` emite la factura del pedido `11`:

```json
{ "orderId": 11, "dueDate": "2026-11-24" }
```

Si esa fecha ya ha pasado, cambia `dueDate` por la fecha actual. Consulta `GET /api/v1/billing/invoices` con `customerId = 10` y compara las dos facturas.

**Fallo observado:** la factura antigua muestra «Calle Roble 9».

**Resultado esperado:** la factura antigua conserva «Calle Olmo 4» en el detalle y en el listado. La ficha del cliente y la nueva factura muestran «Calle Roble 9».

**Para investigar:** pon un breakpoint en [BillingController.get](../../../src/main/java/com/acme/operations/billing/BillingController.java) al leer la factura antigua. Sigue los datos hasta la consulta.

## Comprobar después del arreglo

Usa los datos nuevos: cliente `14`, factura antigua `4` y pedido pendiente de facturar `17`. Cambia la dirección del cliente `14` con:

```json
{
  "name": "L22-0ca05616c1ab424c80af",
  "taxId": "L22-0CA05616C1AB424C80AF",
  "email": "ventas@example.test",
  "version": 0,
  "address": "Calle Roble 9"
}
```

En `POST /api/v1/billing/invoices`, emite la factura con el siguiente cuerpo. Si el vencimiento ya ha pasado, usa la fecha actual:

```json
{ "orderId": 17, "dueDate": "2026-11-24" }
```

Compara después las facturas del cliente `14`.

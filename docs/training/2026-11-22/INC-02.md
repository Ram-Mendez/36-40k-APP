# INC-02 · El abono de una devolución parcial es demasiado alto

**Incidencia.** Se vendieron cuatro unidades del producto `13` a 12,50 € y dos unidades del producto `14` a 3 €. El cliente devuelve una unidad del primero y dos del segundo.

**Datos preparados:** pedido completado `6`.

## Reproducir en Swagger

1. Consulta `GET /api/v1/orders/{id}` con `id = 6` y revisa sus líneas.
2. Envía este cuerpo a `POST /api/v1/returns`:
   {
   "id": 6,
   "customerId": 6,
   "customerName": "L22-cab9d2c7fac3450fa66f",
   "status": "COMPLETED",
   "total": 56,
   "version": 4,
   "shippingReference": "QUOTE-6-56.00",
   "shippingCost": 7.5,
   "createdAt": "2026-11-22T05:28:54.793330Z",
   "updatedAt": "2026-11-22T05:28:55.418872Z",
   "lines": [
   {
   "id": 6,
   "productId": 13,
   "sku": "L22-52B98197DB2D4F26A08F",
   "productName": "L22-52b98197db2d4f26a08f",
   "quantity": 4,
   "unitPrice": 12.5,
   "lineTotal": 50
   },
   {
   "id": 7,
   "productId": 14,
   "sku": "L22-E9CE57D210B047CC9825",
   "productName": "L22-e9ce57d210b047cc9825",
   "quantity": 2,
   "unitPrice": 3,
   "lineTotal": 6
   }
   ]
   }

```json
{
  "orderId": 6,
  "requestId": "673a5feb-f44b-41b7-a9a4-75321a873b28",
  "reason": "Dos referencias devueltas",
  "lines": [
    { "productId": 13, "quantity": 1 },
    { "productId": 14, "quantity": 2 }
  ]
}
```



{
"id": 1,
"orderId": 6,
"status": "REQUESTED",
"reason": "Dos referencias devueltas",
"reviewReason": null,
"creditAmount": 56,
"version": 0,
"createRequestId": "673a5feb-f44b-41b7-a9a4-75321a873b28",
"receiptRequestId": null,
"createdAt": "2026-11-22T08:34:47.547780Z",
"updatedAt": "2026-11-22T08:34:47.547780Z",
"reviewedAt": null,
"receivedAt": null,
"lines": [
{
"productId": 13,
"sku": "L22-52B98197DB2D4F26A08F",
"quantity": 1,
"unitPrice": 12.5,
"creditAmount": 12.5,
"restockQuantity": null,
"damagedQuantity": null
},
{
"productId": 14,
"sku": "L22-E9CE57D210B047CC9825",
"quantity": 2,
"unitPrice": 3,
"creditAmount": 6,
"restockQuantity": null,
"damagedQuantity": null
}
]
}

3. Compara `creditAmount` de la cabecera con los importes de `lines`. Consulta también `GET /api/v1/returns` con `orderId = 6`.
   {
   "content": [
   {
   "id": 1,
   "orderId": 6,
   "status": "REQUESTED",
   "reason": "Dos referencias devueltas",
   "creditAmount": 56,
   "version": 0,
   "createdAt": "2026-11-22T08:34:47.547780Z",
   "reviewedAt": null,
   "receivedAt": null
   }
   ],
   "page": 0,
   "size": 20,
   "totalElements": 1,
   "totalPages": 1
   }

**Fallo observado:** la cabecera muestra 56 € aunque las líneas devueltas suman 18,50 €.

**Resultado esperado:** cabecera `18.50`; líneas `12.50` y `6.00`. Una nueva solicitud de cuatro unidades del producto `13` debe devolver `409 RETURN_QUANTITY_EXCEEDED`, porque solo quedan tres por devolver.

Para comprobar ese exceso, envía otra vez a `POST /api/v1/returns`:

```json
{
  "orderId": 6,
  "requestId": "2c104bf2-83e1-499b-9686-4a98fd0e13e4",
  "reason": "Comprobar exceso",
  "lines": [{ "productId": 13, "quantity": 4 }]
}
```

**Para investigar:** pon un breakpoint en [ReturnController.create](../../../src/main/java/com/acme/operations/returns/ReturnController.java) y sigue el cálculo. No cambies los precios de la venta para resolverlo.

## Comprobar después del arreglo

Los pedidos `6` y `14` ya tienen una devolución guardada. Usa el pedido `18`, preparado para esta comprobación. En `GET /api/v1/orders/18` verás cuatro unidades del producto `35` a 12,50 € y dos del `36` a 3 €. La venta suma 56,00 €.

En Swagger, envía a `POST /api/v1/returns` la devolución de una unidad del primer producto y dos del segundo.

```json
{
  "orderId": 18,
  "requestId": "e22567c1-46f9-4884-b1db-c82d9f2050e0",
  "reason": "Dos referencias devueltas",
  "lines": [
    { "productId": 35, "quantity": 1 },
    { "productId": 36, "quantity": 2 }
  ]
}
```

Comprueba que `creditAmount` vale `18.50` tanto en la respuesta como en `GET /api/v1/returns` con `orderId = 18`. Después, pide cuatro unidades del producto `35` con este cuerpo. Quedan tres disponibles para devolver y se espera `409 RETURN_QUANTITY_EXCEEDED`.

```json
{
  "orderId": 18,
  "requestId": "c4d1273e-9355-4057-bae0-01496edda076",
  "reason": "Comprobar exceso",
  "lines": [{ "productId": 35, "quantity": 4 }]
}
```

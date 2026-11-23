# INC-07 · La compra se cierra tras la primera entrega

**Incidencia.** Una compra tiene dos unidades de A y tres de B. Llegan en albaranes distintos. La compra se marca como recibida después de la primera entrega, aunque B sigue pendiente.

**Datos preparados:** compra `3`; producto A `20`; producto B `21`.

## Reproducir en Swagger

1. Consulta `GET /api/v1/purchase-orders/{id}` con `id = 3`.
2. En `POST /api/v1/purchase-orders/{id}/receipts`, pon `id = 3` y recibe A:

```json
{
  "requestId": "3582972b-ad06-41c4-b6f9-7050ab1a7e77",
  "deliveryReference": "L22-ab73d689778d44a389b2",
  "lines": [{ "productId": 20, "quantity": 2 }]
}
```
{
"id": 2,
"purchaseOrderId": 3,
"requestId": "3582972b-ad06-41c4-b6f9-7050ab1a7e77",
"deliveryReference": "L22-ab73d689778d44a389b2",
"receivedAt": "2026-11-23T04:06:42.710169Z",
"lines": [
{
"productId": 20,
"quantity": 2
}
]
}

3. Vuelve a consultar la compra. Compara `status` con `outstandingQuantity` de B.

{
"id": 3,
"supplierId": 2,
"supplierName": "L22-90a9e7468fe44d62afe3",
"status": "RECEIVED",
"expectedOn": "2026-11-24",
"total": 25,
"version": 4,
"createdAt": "2026-11-22T05:29:27.931421Z",
"updatedAt": "2026-11-23T04:06:42.710169Z",
"lines": [
{
"productId": 20,
"sku": "L22-955B0B1B0ED5449798F0",
"productName": "L22-955b0b1b0ed5449798f0",
"quantity": 2,
"receivedQuantity": 2,
"outstandingQuantity": 0,
"unitCost": 5,
"lineTotal": 10
},
{
"productId": 21,
"sku": "L22-F7A7630828C342E49256",
"productName": "L22-f7a7630828c342e49256",
"quantity": 3,
"receivedQuantity": 0,
"outstandingQuantity": 3,
"unitCost": 5,
"lineTotal": 15
}
]
}
4. Intenta recibir B en la misma compra con este cuerpo:

```json
{
  "requestId": "0a76adf5-ccbc-4807-a8ae-1051d800f6a4",
  "deliveryReference": "L22-8f0f449e733f41788414",
  "lines": [{ "productId": 21, "quantity": 3 }]
}
```

**Fallo observado:** la compra queda `RECEIVED` tras recibir A y rechaza el segundo albarán.

**Resultado esperado:** tras la primera entrega queda `PARTIALLY_RECEIVED`. La segunda recepción responde 200 y entonces la compra pasa a `RECEIVED`. Hay dos albaranes y los saldos físicos son dos unidades de A y tres de B.

**Para investigar:** pon un breakpoint en [PurchaseOrderController.receive](../../../src/main/java/com/acme/operations/procurement/PurchaseOrderController.java) y sigue cómo cambia el estado.

## Comprobar después del arreglo

Usa la compra nueva `6`. Primero recibe A con:

```json
{
  "requestId": "ed615cc2-1756-4310-9d7f-66384206dbc3",
  "deliveryReference": "L22-235c5930cc0d4814ab05",
  "lines": [{ "productId": 32, "quantity": 2 }]
}
```
{
"id": 3,
"purchaseOrderId": 6,
"requestId": "ed615cc2-1756-4310-9d7f-66384206dbc3",
"deliveryReference": "L22-235c5930cc0d4814ab05",
"receivedAt": "2026-11-23T04:13:56.416339Z",
"lines": [
{
"productId": 32,
"quantity": 2
}
]
}
Comprueba el estado y recibe B en la misma compra con:

```json
{
  "requestId": "41c766b3-2aa4-42d9-bc27-0dda795f8739",
  "deliveryReference": "L22-19f42e808c2c486cb1bc",
  "lines": [{ "productId": 33, "quantity": 3 }]
}
```

Consulta la compra `6`, sus recepciones y el inventario de los productos `32` y `33`.

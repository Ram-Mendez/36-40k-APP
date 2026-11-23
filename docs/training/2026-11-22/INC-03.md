# INC-03 · Una entrega aumenta el stock previsto

**Incidencia.** Hay una compra enviada de 10 unidades
                    y otra en borrador de 20.
Al recibir cuatro unidades,
el stock previsto sube de 10 a 14
sin que se haya comprado nada más.

**Datos preparados:** producto `15`; compra enviada `1`; objetivo de stock `25`.

## Reproducir en Swagger

1. Consulta `GET /api/v1/replenishment/proposals` con `productId = 15`. Anota `available`, `incoming`, `projected` y `suggestedQuantity`.
   {
   "content": [
   {
   "productId": 15,
   "sku": "L22-8181CB0AF8A74CC88BF4",
   "productName": "L22-8181cb0af8a74cc88bf4",
   "minimumStock": 20,
   "targetStock": 25,
   "available": 0,
   "incoming": 10,
2.
   "projected": 10,
2.
   "suggestedQuantity": 15,
3.
   "preferredSupplierId": 1,
   "supplierName": "L22-6f01694ed5f94496be4f",
   "supplierActive": true,
   "purchasable": true,
   "blockingReason": null,
   "policyVersion": 0
   }
   ],
   "page": 0,
   "size": 20,
   "totalElements": 1,
   "totalPages": 1
   }
2. En `POST /api/v1/purchase-orders/{id}/receipts`, pon `id = 1` y envía:

```json
{
  "requestId": "3f70f2f8-5911-4642-9ea9-89d40ecc2b3d",
  "deliveryReference": "L22-8f5e6415a97d48f7ac52",
  "lines": [{ "productId": 15, "quantity": 4 }]
}
```
{
"id": 1,
"purchaseOrderId": 1,
"requestId": "3f70f2f8-5911-4642-9ea9-89d40ecc2b3d",
"deliveryReference": "L22-8f5e6415a97d48f7ac52",
"receivedAt": "2026-11-22T09:55:57.296143Z",
"lines": [
{
"productId": 15,
"quantity": 4
}
]
}

3. Consulta `GET /api/v1/purchase-orders/{id}` con `id = 1`,
4. {
   "id": 1,
   "supplierId": 1,
   "supplierName": "L22-6f01694ed5f94496be4f",
   "status": "PARTIALLY_RECEIVED",
   "expectedOn": "2026-11-24",
   "total": 50,
   "version": 3,
   "createdAt": "2026-11-22T05:29:02.397027Z",
   "updatedAt": "2026-11-22T09:55:57.296143Z",
   "lines": [
   {
   "productId": 15,
   "sku": "L22-8181CB0AF8A74CC88BF4",
   "productName": "L22-8181cb0af8a74cc88bf4",
   "quantity": 10,
   "receivedQuantity": 4,
   "outstandingQuantity": 6,
   "unitCost": 5,
   "lineTotal": 50
   }
   ]
   }
5. `GET /api/v1/inventory` con `productId = 15` y de nuevo la propuesta del paso 1.
   {
   "content": [
   {
   "productId": 15,
   "sku": "L22-8181CB0AF8A74CC88BF4",
   "productName": "L22-8181cb0af8a74cc88bf4",
   "onHand": 4,
   "reserved": 0,
   "available": 4
   }
   ],
   "page": 0,
   "size": 20,
   "totalElements": 1,
   "totalPages": 1
   }

**Fallo observado:** después de la recepción, `projected` pasa a `14`.

{
"content": [
{
"productId": 15,
"sku": "L22-8181CB0AF8A74CC88BF4",
"productName": "L22-8181cb0af8a74cc88bf4",
"minimumStock": 20,
"targetStock": 25,
"available": 4,
"incoming": 10,
"projected": 14,
"suggestedQuantity": 11,
"preferredSupplierId": 1,
"supplierName": "L22-6f01694ed5f94496be4f",
"supplierActive": true,
"purchasable": true,
"blockingReason": null,
"policyVersion": 0
}
],
"page": 0,
"size": 20,
"totalElements": 1,
"totalPages": 1
}

**Resultado esperado:** `available = 4`, `incoming = 6`, `projected = 10` y `suggestedQuantity = 15`. La compra en borrador no cuenta como mercancía pendiente de recibir.

**Para investigar:** pon un breakpoint en [ReplenishmentController.proposals](../../../src/main/java/com/acme/operations/replenishment/ReplenishmentController.java) y sigue los datos hasta el repositorio.

## Comprobar después del arreglo

Usa la compra nueva `4` y el producto `29`. Repite los pasos anteriores y registra la recepción con este cuerpo:

```json
{
  "requestId": "ac599d98-2166-4102-ae16-c1eaa5c2795f",
  "deliveryReference": "L22-b4e2e211b30a4a9eadfa",
  "lines": [{ "productId": 29, "quantity": 4 }]
}
```

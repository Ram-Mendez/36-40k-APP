# INC-06 · Una confirmación rechazada deja stock reservado

**Incidencia.** Un pedido pide dos unidades de A y una de B. B se desactivó después de añadirlo. Al confirmar, la operación se rechaza, pero dos unidades de A quedan reservadas.

**Datos preparados:** pedido `9`; producto A `18`; producto B `19`. A tiene cinco unidades físicas y ninguna reserva previa.

## Reproducir en Swagger

1. Consulta `GET /api/v1/orders/{id}` con `id = 9`
2. {
   "id": 9,
   "customerId": 9,
   "customerName": "L22-d538d249d81e4a4faf0d",
   "status": "DRAFT",
   "total": 30,
   "version": 2,
   "shippingReference": null,
   "shippingCost": null,
   "createdAt": "2026-11-22T05:29:21.787839Z",
   "updatedAt": "2026-11-22T05:29:21.874709Z",
   "lines": [
   {
   "id": 10,
   "productId": 18,
   "sku": "L22-1378805444A747AB8A23",
   "productName": "L22-1378805444a747ab8a23",
   "quantity": 2,
   "unitPrice": 10,
   "lineTotal": 20
   },
   {
   "id": 11,
   "productId": 19,
   "sku": "L22-33DBCE58A19E4A66AC91",
   "productName": "L22-33dbce58a19e4a66ac91",
   "quantity": 1,
   "unitPrice": 10,
   "lineTotal": 10
   }
   ]
   }
3. y `GET /api/v1/inventory` con `productId = 18`.
   {
   "content": [
   {
   "productId": 18,
   "sku": "L22-1378805444A747AB8A23",
   "productName": "L22-1378805444a747ab8a23",
   "onHand": 5,
   "reserved": 0,
   "available": 5
   }
   ],
   "page": 0,
   "size": 20,
   "totalElements": 1,
   "totalPages": 1
   }
2. Ejecuta `POST /api/v1/orders/{id}/confirm` con `id = 9`. Debe responder `409 PRODUCT_UNAVAILABLE`.
3. {
   "type": "urn:business-operations:error:product_unavailable",
   "title": "Conflict",
   "status": 409,
   "detail": "Product is missing or inactive",
   "instance": "/api/v1/orders/9/confirm",
   "code": "PRODUCT_UNAVAILABLE",
   "requestId": "ea82d2a3-96fb-41c6-8a0d-88f792f0869f"
   }
3. Antes de quitar ninguna línea, vuelve a consultar el pedido, el inventario de `18` y `GET /api/v1/inventory/movements` con `productId = 18`.

**Fallo observado:** el pedido sigue en `DRAFT`, pero A tiene `reserved = 2` y hay un movimiento de reserva para ese pedido.

**Resultado esperado:** tras el rechazo, el pedido sigue en `DRAFT`, A tiene `reserved = 0` y el pedido no tiene movimientos. Si se retira B y se vuelve a confirmar, deben reservarse exactamente dos unidades de A.

**Para investigar:** pon un breakpoint en [OrderController.confirm](../../../src/main/java/com/acme/operations/order/OrderController.java) y sigue el recorrido de las dos líneas.

## Comprobar después del arreglo

Usa el pedido nuevo `15`, con A `30` y B `31`. Repite el rechazo y comprueba el inventario de `30`. Después ejecuta `DELETE /api/v1/orders/{id}/lines/{productId}` con `id = 15` y `productId = 31`, confirma el pedido `15` y comprueba que A tiene exactamente dos reservas.
ç


{
"id": 15,
"customerId": 13,
"customerName": "L22-84fd15072b264de88a7d",
"status": "DRAFT",
"total": 30,
"version": 2,
"shippingReference": null,
"shippingCost": null,
"createdAt": "2026-11-22T05:30:12.736483Z",
"updatedAt": "2026-11-22T05:30:12.827716Z",
"lines": [
{
"id": 18,
"productId": 30,
"sku": "L22-AE2896966B91459DA308",
"productName": "L22-ae2896966b91459da308",
"quantity": 2,
"unitPrice": 10,
"lineTotal": 20
},
{
"id": 19,
"productId": 31,
"sku": "L22-B1A4AC8F27EC48178222",
"productName": "L22-b1a4ac8f27ec48178222",
"quantity": 1,
"unitPrice": 10,
"lineTotal": 10
}
]
}
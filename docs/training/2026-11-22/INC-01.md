# INC-01 · Un pedido pierde una línea al paginar

**Incidencia.** Un cliente tiene dos pedidos. El primero contiene dos productos por un total de 20 €; el segundo está vacío. Al listar un pedido por página, el primero aparece con una sola línea.

**Datos preparados:** cliente `5`; primer pedido `4`. Están en la base `lab_20261117`.

## Reproducir en Swagger

1. Consulta `GET /api/v1/orders/{id}` con `id = 4`. Comprueba que tiene dos líneas y un total de 20 €.
2.
2. En `GET /api/v1/orders` usa `customerId = 5`, `page = 0`, `size = 1` y `sort = createdAt,asc`. Compara las líneas con el detalle.
3. Repite el listado con `size = 3`. Después consulta `page = 1` y `size = 1`.

**Fallo observado:** con `size = 1` falta una línea del primer pedido, aunque su total sigue siendo 20 €.

**Resultado esperado:** el pedido debe tener las mismas dos líneas con cualquier tamaño de página. Con `size = 1` hay dos páginas y la segunda contiene el pedido vacío.

**Para investigar:** pon un breakpoint en [OrderController.search](../../../src/main/java/com/acme/operations/order/OrderController.java) y sigue la petición hasta el acceso a PostgreSQL. Corrige el backend y repite las mismas consultas.


{
"id": 4,
"customerId": 5,
"customerName": "L22-e18531a9c03a4fad8c19",
"status": "DRAFT",
"total": 20,
"version": 2,
"shippingReference": null,
"shippingCost": null,
"createdAt": "2026-11-22T05:28:48.300562Z",
"updatedAt": "2026-11-22T05:28:48.399041Z",
"lines": [
{
"id": 4,
"productId": 11,
"sku": "L22-62B7AEEB676F49F69CCC",
"productName": "L22-62b7aeeb676f49f69ccc",
"quantity": 1,
"unitPrice": 10,
"lineTotal": 10
},
{
"id": 5,
"productId": 12,
"sku": "L22-E13CBC3DAC304D14A6FF",
"productName": "L22-e13cbc3dac304d14a6ff",
"quantity": 2,
"unitPrice": 5,
"lineTotal": 10
}
]
}-
-------
-
{
"content": [
{
"id": 4,
"customerId": 5,
"customerName": "L22-e18531a9c03a4fad8c19",
"status": "DRAFT",
"total": 20,
"version": 2,
"shippingReference": null,
"shippingCost": null,
"createdAt": "2026-11-22T05:28:48.300562Z",
"updatedAt": "2026-11-22T05:28:48.399041Z",
"lines": [
{
"id": 4,
"productId": 11,
"sku": "L22-62B7AEEB676F49F69CCC",
"productName": "L22-62b7aeeb676f49f69ccc",
"quantity": 1,
"unitPrice": 10,
"lineTotal": 10
}
]
}
],
"page": 0,
"size": 1,
"totalElements": 2,
"totalPages": 2
}-
-------
-
{
"content": [
{
"id": 4,
"customerId": 5,
"customerName": "L22-e18531a9c03a4fad8c19",
"status": "DRAFT",
"total": 20,
"version": 2,
"shippingReference": null,
"shippingCost": null,
"createdAt": "2026-11-22T05:28:48.300562Z",
"updatedAt": "2026-11-22T05:28:48.399041Z",
"lines": [
{
"id": 4,
"productId": 11,
"sku": "L22-62B7AEEB676F49F69CCC",
"productName": "L22-62b7aeeb676f49f69ccc",
"quantity": 1,
"unitPrice": 10,
"lineTotal": 10
},
{
"id": 5,
"productId": 12,
"sku": "L22-E13CBC3DAC304D14A6FF",
"productName": "L22-e13cbc3dac304d14a6ff",
"quantity": 2,
"unitPrice": 5,
"lineTotal": 10
}
]
},
{
"id": 5,
"customerId": 5,
"customerName": "L22-e18531a9c03a4fad8c19",
"status": "DRAFT",
"total": 0,
"version": 0,
"shippingReference": null,
"shippingCost": null,
"createdAt": "2026-11-22T05:28:48.455253Z",
"updatedAt": "2026-11-22T05:28:48.455253Z",
"lines": []
}
],
"page": 0,
"size": 3,
"totalElements": 2,
"totalPages": 1
}{
"content": [
{
"id": 4,
"customerId": 5,
"customerName": "L22-e18531a9c03a4fad8c19",
"status": "DRAFT",
"total": 20,
"version": 2,
"shippingReference": null,
"shippingCost": null,
"createdAt": "2026-11-22T05:28:48.300562Z",
"updatedAt": "2026-11-22T05:28:48.399041Z",
"lines": [
{
"id": 4,
"productId": 11,
"sku": "L22-62B7AEEB676F49F69CCC",
"productName": "L22-62b7aeeb676f49f69ccc",
"quantity": 1,
"unitPrice": 10,
"lineTotal": 10
},
{
"id": 5,
"productId": 12,
"sku": "L22-E13CBC3DAC304D14A6FF",
"productName": "L22-e13cbc3dac304d14a6ff",
"quantity": 2,
"unitPrice": 5,
"lineTotal": 10
}
]
},
{
"id": 5,
"customerId": 5,
"customerName": "L22-e18531a9c03a4fad8c19",
"status": "DRAFT",
"total": 0,
"version": 0,
"shippingReference": null,
"shippingCost": null,
"createdAt": "2026-11-22T05:28:48.455253Z",
"updatedAt": "2026-11-22T05:28:48.455253Z",
"lines": []
}
],
"page": 0,
"size": 3,
"totalElements": 2,
"totalPages": 1
}
-------
{
"content": [
{
"id": 5,
"customerId": 5,
"customerName": "L22-e18531a9c03a4fad8c19",
"status": "DRAFT",
"total": 0,
"version": 0,
"shippingReference": null,
"shippingCost": null,
"createdAt": "2026-11-22T05:28:48.455253Z",
"updatedAt": "2026-11-22T05:28:48.455253Z",
"lines": []
}
],
"page": 1,
"size": 1,
"totalElements": 2,
"totalPages": 2
}

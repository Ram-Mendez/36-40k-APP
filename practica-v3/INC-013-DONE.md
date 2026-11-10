# INC-013 · La cabecera del presupuesto suma menos que sus líneas

**Abierta · Prioridad alta · Ventas**
**Informante:** Comercial de distribución.

> El cliente ve dos artículos distintos en el presupuesto. Cada línea marca 12 €, pero al pie solo aparecen 12 € en total.

**Muestra de soporte.** Crea un borrador para un cliente activo. Añade dos unidades de un producto de 6,00 € y tres unidades de otro de 4,00 €. Vuelve a abrir el borrador por su ID. Las dos líneas están presentes y el importe de cabecera es 12,00 €. Si cambias la cantidad de la segunda línea a cuatro unidades, la cabecera muestra 28,00 €.

**Criterios de aceptación**

- Con las dos líneas de 12,00 €, tanto la respuesta al guardar como la lectura posterior muestran un total de 24,00 €.
- Al cambiar la segunda cantidad a cuatro, el total es 28,00 €. Al quitar esa segunda línea, el pedido vuelve a 12,00 € y conserva la primera.



>
> product 12 13
> cuestomer 4
> order 2-3
> los productos tienen 10 unidades de inventario
>
> {
"id": 2,
"customerId": 4,
"customerName": "clienteVip",
"status": "DRAFT",
"total": 12,
"version": 2,
"shippingReference": null,
"shippingCost": null,
"createdAt": "2026-11-09T08:18:51.087996Z",
"updatedAt": "2026-11-09T08:21:37.645358Z",
"lines": [
{
"id": 2,
"productId": 12,
"sku": "BUG3-A",
"productName": "mi producto",
"quantity": 1,
"unitPrice": 12,
"lineTotal": 12
},
{
"id": 3,
"productId": 13,
"sku": "BUG3-B",
"productName": "mi producto",
"quantity": 1,
"unitPrice": 12,
"lineTotal": 12
}
]
}
> bug reproducido -- el total debe ser en estado 24.
{
"id": 2,
"customerId": 4,
"customerName": "clienteVip",
"status": "DRAFT",
"total": 24,
"version": 7,
"shippingReference": null,
"shippingCost": null,
"createdAt": "2026-11-09T08:18:51.087996Z",
"updatedAt": "2026-11-09T08:56:15.904345Z",
"lines": [
{
"id": 2,
"productId": 12,
"sku": "BUG3-A",
"productName": "mi producto",
"quantity": 1,
"unitPrice": 12,
"lineTotal": 12
},
{
"id": 3,
"productId": 13,
"sku": "BUG3-B",
"productName": "mi producto",
"quantity": 1,
"unitPrice": 12,
"lineTotal": 12
}
]
}
Qué hice:  quitando el DISTINCT en la query del recalculo calcula correctamente el total del pedido
Qué sospeché: DISTINCT en la query del recalculo calculaba precio de producto  y cantidades DIFERENTES
Qué observé: el total del pedido no daba un resultado coherente conforme a las lineas 
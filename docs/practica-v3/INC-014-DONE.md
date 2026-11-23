# INC-014 · El usuario de consulta ve el pedido, pero no puede abrirlo

**Abierta · Prioridad media · Atención al cliente**
**Informante:** Supervisora de atención telefónica.

> Puedo encontrar el pedido en la lista,
>{
"content": [
{
"id": 3,
"customerId": 4,
"customerName": "clienteVip",
"status": "DRAFT",
"total": 0,
"version": 0,
"shippingReference": null,
"shippingCost": null,
"createdAt": "2026-11-09T08:18:53.155421Z",
"updatedAt": "2026-11-09T08:18:53.155421Z",
"lines": []
},
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
],
"page": 0,
"size": 20,
"totalElements": 2,
"totalPages": 1
} 
pero cuando el cliente pregunta por sus líneas e intento abrir el detalle me sale «acceso denegado».

**Muestra de soporte.** Con `operations`, crea un pedido y guarda el ID. 3
Inicia sesión como `readonly`. Con ese mismo token pide el listado de pedidos del cliente
{
"content": [
{
"id": 3,
"customerId": 4,
"customerName": "clienteVip",
"status": "DRAFT",
"total": 0,
"version": 0,
"shippingReference": null,
"shippingCost": null,
"createdAt": "2026-11-09T08:18:53.155421Z",
"updatedAt": "2026-11-09T08:18:53.155421Z",
"lines": []
},
{
"id": 2,
"customerId": 4,
"customerName": "clienteVip",
"status": "DRAFT",
"total": 24,
"version": 8,
"shippingReference": null,
"shippingCost": null,
"createdAt": "2026-11-09T08:18:51.087996Z",
"updatedAt": "2026-11-09T09:17:17.433779Z",
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
],
"page": 0,
"size": 20,
"totalElements": 2,
"totalPages": 1
}
y abre `GET /api/v1/orders/{pedido}` con el ID encontrado. El listado devuelve 200 y el detalle devuelve 403. Al consultar ese mismo detalle con `operations`, obtienes 200.

**Criterios de aceptación**

- Con un token de `readonly` vigente, el listado y el detalle devuelven 200 y muestran el mismo pedido.
- Con `readonly`, intentar crear un pedido válido sigue devolviendo 403. Sin sesión, consultar el detalle sigue devolviendo 401.

Qué hice: Añadir autorizacion a readonly para esta operacion
@GetMapping("/{id}")
@PreAuthorize("hasAnyRole('ADMIN','OPERATIONS','READONLY')")
public OrderDtos.Order get(@PathVariable long id) {
return service.get(id);
}

Qué sospeché: 403 es un error de autorizacion y la raiz de permisos está en el controlador o en alguna configuracion especifica para el readonly
Qué observé: 403 en un pedido por id, mientras que en listado de pedidos no hay problema
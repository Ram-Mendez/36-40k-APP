# INC-22 · No se puede cancelar un pedido en borrador

Ventas quiere descartar un borrador que nunca llegó a confirmar. El cliente recibe un conflicto de estado.

1. Haz `GET /api/v1/orders/34` y comprueba `status = DRAFT`.
2. {
   "id": 34,
   "customerId": 21,
   "customerName": "Cliente práctica 3",
   "status": "DRAFT",
   "total": 0,
   "version": 0,
   "shippingReference": null,
   "shippingCost": null,
   "createdAt": "2026-11-23T12:17:46.942306Z",
   "updatedAt": "2026-11-23T12:17:46.942306Z",
   "lines": []
   }
2. Ejecuta `POST /api/v1/orders/34/cancel`. El servicio responde `409` y el pedido sigue en `DRAFT`.
3. Tras el arreglo, el POST debe responder `200` con `status = CANCELLED`. También puedes verificarlo con el borrador `36`.

Empieza en [OrderController.cancel](../../../src/main/java/com/acme/operations/order/OrderController.java) y sigue la petición con el depurador.

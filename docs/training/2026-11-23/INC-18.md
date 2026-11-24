# INC-18 · Al rechazar una devolución queda aprobada

Soporte rechaza una devolución y el sistema la deja lista para recibir mercancía.

1. Abre `GET /api/v1/returns/11` y comprueba `status = REQUESTED` y `version = 0`.
   {
   "id": 13,
   "orderId": 31,
   "status": "REQUESTED",
   "reason": "Incidencia de devolución",
   "reviewReason": null,
   "creditAmount": 10,
   "version": 0,
   "createRequestId": "a8e49305-41aa-44d3-8738-9e53ca25465b",
   "receiptRequestId": null,
   "createdAt": "2026-11-24T06:29:44.371318Z",
   "updatedAt": "2026-11-24T06:29:44.371318Z",
   "reviewedAt": null,
   "receivedAt": null,
   "lines": [
   {
   "productId": 45,
   "sku": "T24-BAL-1",
   "quantity": 1,
   "unitPrice": 10,
   "creditAmount": 10,
   "restockQuantity": null,
   "damagedQuantity": null
   }
   ]
   }
2. Haz `POST /api/v1/returns/11/reject` con `{"version":0,"reason":"Fuera de plazo"}`. El `200` devuelve `status = APPROVED`.
3. {
   "id": 11,
   "orderId": 35,
   "status": "APPROVED",
   "reason": "Incidencia de devolución",
   "reviewReason": "Fuera de plazo",
   "creditAmount": 10,
   "version": 1,
   "createRequestId": "e5da0b13-ee4a-430d-989d-8cf50399f7d0",
   "receiptRequestId": null,
   "createdAt": "2026-11-23T12:17:46.942306Z",
   "updatedAt": "2026-11-24T06:23:50.834891Z",
   "reviewedAt": "2026-11-24T06:23:50.834891Z",
   "receivedAt": null,
   "lines": [
   {
   "productId": 47,
   "sku": "T24-BAL-3",
   "quantity": 1,
   "unitPrice": 10,
   "creditAmount": 10,
   "restockQuantity": null,
   "damagedQuantity": null
   }
   ]
   }
3. Para verificar el arreglo, repite el POST con la devolución `12`, que está preparada en `REQUESTED`. Debe devolver `status = REJECTED` y conservar `reviewReason = Fuera de plazo`.

Empieza en [ReturnController.reject](../../../src/main/java/com/acme/operations/returns/ReturnController.java) y sigue la petición con el depurador.

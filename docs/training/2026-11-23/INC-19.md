# INC-19 · Una compra enviada figura como una edición en auditoría

La compra pasa a enviada, pero el historial registra un código de edición. El informe de compras enviadas no encuentra el evento.

1. Autoriza Swagger con `admin` para poder consultar auditoría. Ejecuta `POST /api/v1/purchase-orders/15/submit` y comprueba `status = SUBMITTED`.
2. {
   "id": 15,
   "supplierId": 9,
   "supplierName": "Proveedor práctica 15 a 24",
   "status": "SUBMITTED",
   "expectedOn": "2026-12-15",
   "total": 5,
   "version": 1,
   "createdAt": "2026-11-23T12:17:46.942306Z",
   "updatedAt": "2026-11-24T06:31:56.296483Z",
   "lines": [
   {
   "productId": 47,
   "sku": "T24-BAL-3",
   "productName": "Balanza práctica 3",
   "quantity": 1,
   "receivedQuantity": 0,
   "outstandingQuantity": 1,
   "unitCost": 5,
   "lineTotal": 5
   }
   ]
   }
2. En `GET /api/v1/audit`, usa `entityType = PURCHASE_ORDER` y `entityId = 15`. El campo `action` del evento de envío dice `PURCHASE_ORDER_UPDATED`.
3. Tras el arreglo, envía la compra `16` y consulta su auditoría. Debe registrar `action = PURCHASE_ORDER_SUBMITTED`.

Empieza en [PurchaseOrderController.submit](../../../src/main/java/com/acme/operations/procurement/PurchaseOrderController.java) y sigue la petición con el depurador.

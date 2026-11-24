# INC-24 · El albarán de la recepción muestra un UUID

Al abrir una recepción, el almacén ve un UUID donde espera la referencia que figura en el albarán.

1. Ejecuta `GET /api/v1/purchase-orders/17/receipts/6`.
2. {
   "id": 6,
   "purchaseOrderId": 17,
   "requestId": "6787de52-b679-4642-9f36-b74320c55848",
   "deliveryReference": "6787de52-b679-4642-9f36-b74320c55848",
   "receivedAt": "2026-11-23T12:17:46.942306Z",
   "lines": [
   {
   "productId": 47,
   "quantity": 2
   }
   ]
   }
2. Compara `deliveryReference` y `requestId`. Actualmente ambos valen `6787de52-b679-4642-9f36-b74320c55848`.
3. Tras el arreglo, `deliveryReference` debe ser `ALB-T24-842`. Comprueba el mismo campo en `GET /api/v1/purchase-orders/17/receipts`.

Empieza en [PurchaseOrderController.receipt](../../../src/main/java/com/acme/operations/procurement/PurchaseOrderController.java) y sigue la petición con el depurador.

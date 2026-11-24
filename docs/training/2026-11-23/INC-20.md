# INC-20 · Las unidades reservadas llegan como texto en el JSON

El consumidor del inventario espera cantidades numéricas. El JSON del saldo devuelve las reservas entre comillas.

1. Ejecuta `GET /api/v1/inventory` con `productId = 47`.
2. {
   "content": [
   {
   "productId": 47,
   "sku": "T24-BAL-3",
   "productName": "Balanza práctica 3",
   "onHand": 212,
   "reserved": "0",
   "available": 212
   }
   ],
   "page": 0,
   "size": 20,
   "totalElements": 1,
   "totalPages": 1
   }
2. En el JSON, `content[0].reserved` aparece como `"0"`. `onHand` y `available` aparecen como números.
3. Tras el arreglo, repite el GET y comprueba que el campo sea `"reserved": 0`.

Empieza en [InventoryController.balances](../../../src/main/java/com/acme/operations/inventory/InventoryController.java) y sigue la petición con el depurador.

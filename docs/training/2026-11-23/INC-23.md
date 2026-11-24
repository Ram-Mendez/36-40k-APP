# INC-23 · La política de reposición queda activa al desactivarla

Compras desactiva una política y recibe `200`. El estado guardado sigue activo.

1. Ejecuta `GET /api/v1/replenishment/policies/47` y anota `version`.
2. {
   "content": [
   {
   "productId": 47,
   "sku": "T24-BAL-3",
   "productName": "Balanza práctica 3",
   "productActive": true,
   "minimumStock": 5,
   "targetStock": 20,
   "preferredSupplierId": null,
   "supplierName": null,
   "supplierActive": null,
   "active": true,
   "version": 0,
   "createdAt": "2026-11-23T12:17:46.942306Z",
   "updatedAt": "2026-11-23T12:17:46.942306Z"
   }
   ],
   "page": 0,
   "size": 20,
   "totalElements": 1,
   "totalPages": 1
   }
2. Ejecuta `PUT /api/v1/replenishment/policies/47` con `{"minimumStock":5,"targetStock":20,"active":false,"version":0}`. Usa la versión del GET si cambió.
3. {
   "productId": 47,
   "sku": "T24-BAL-3",
   "productName": "Balanza práctica 3",
   "productActive": true,
   "minimumStock": 5,
   "targetStock": 20,
   "preferredSupplierId": null,
   "supplierName": null,
   "supplierActive": null,
   "active": true,
   "version": 1,
   "createdAt": "2026-11-23T12:17:46.942306Z",
   "updatedAt": "2026-11-24T07:04:08.838473Z"
   }
3. El PUT incrementa `version` y sigue devolviendo `active = true`. Tras el arreglo, vuelve a enviar `active = false` con la versión vigente y verifica el estado con otro GET.

Empieza en [ReplenishmentController.upsert](../../../src/main/java/com/acme/operations/replenishment/ReplenishmentController.java) y sigue la petición con el depurador.

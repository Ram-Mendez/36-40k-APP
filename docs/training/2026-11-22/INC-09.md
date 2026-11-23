# INC-09 · La propuesta muestra un proveedor que no existe

**Incidencia.** La política de un producto no tiene proveedor asignado. La propuesta muestra el ID `0` y atribuye el bloqueo a un proveedor inactivo.

**Datos preparados:** producto `23` sin proveedor; producto `24` con proveedor activo; producto `25` con proveedor desactivado.

## Reproducir en Swagger

1. Consulta `GET /api/v1/replenishment/policies/{productId}` con `productId = 23` y observa `preferredSupplierId`.
2.       "preferredSupplierId": null,
2. Consulta `GET /api/v1/replenishment/proposals` tres veces: con `productId = 23`,
3.
Response body
Download
{
"content": [
{
"productId": 23,
"sku": "L22-CE7F383D466C4E7FB298",
"productName": "L22-ce7f383d466c4e7fb298",
"minimumStock": 20,
"targetStock": 25,
"available": 0,
"incoming": 0,
"projected": 0,
"suggestedQuantity": 25,
"preferredSupplierId": 0,
"supplierName": null,
"supplierActive": null,
"purchasable": false,
"blockingReason": "SUPPLIER_INACTIVE",
"policyVersion": 0
}
],
"page": 0,
"size": 20,
"totalElements": 1,
"totalPages": 1
}
4. después `24` y
5. {
   "content": [
   {
   "productId": 24,
   "sku": "L22-962F98C95F4F49BE9727",
   "productName": "L22-962f98c95f4f49be9727",
   "minimumStock": 20,
   "targetStock": 25,
   "available": 0,
   "incoming": 0,
   "projected": 0,
   "suggestedQuantity": 25,
   "preferredSupplierId": 3,
   "supplierName": "L22-cede17cb9f2745f3ba20",
   "supplierActive": true,
   "purchasable": true,
   "blockingReason": null,
   "policyVersion": 0
   }
   ],
   "page": 0,
   "size": 20,
   "totalElements": 1,
   "totalPages": 1
   }
6. después `25`.
   {
   "content": [
   {
   "productId": 25,
   "sku": "L22-437B14F98CFA4C958DD8",
   "productName": "L22-437b14f98cfa4c958dd8",
   "minimumStock": 20,
   "targetStock": 25,
   "available": 0,
   "incoming": 0,
   "projected": 0,
   "suggestedQuantity": 25,
   "preferredSupplierId": 4,
   "supplierName": "L22-2e922c9668c641328b1b",
   "supplierActive": false,
   "purchasable": false,
   "blockingReason": "SUPPLIER_INACTIVE",
   "policyVersion": 0
   }
   ],
   "page": 0,
   "size": 20,
   "totalElements": 1,
   "totalPages": 1
   }
3. Compara `preferredSupplierId`, `supplierActive`, `purchasable` y `blockingReason`.
   "preferredSupplierId": 0,
   "supplierActive": null,
   "purchasable": false,
   "blockingReason": "SUPPLIER_INACTIVE",
4.
   "preferredSupplierId": 3,
   "supplierActive": true,
   "purchasable": true,
   "blockingReason": null,
5.
   "preferredSupplierId": 4,
   "supplierActive": false,
   "purchasable": false,
   "blockingReason": "SUPPLIER_INACTIVE",
6.

**Fallo observado:** la política del producto `23` indica `null`, pero su propuesta muestra `0`.

**Resultado esperado:** sin proveedor, el ID y `supplierActive` son `null` y el motivo es `NO_PREFERRED_SUPPLIER`. Con proveedor activo, `purchasable` es `true`. Con proveedor desactivado se conserva su ID y el motivo es `SUPPLIER_INACTIVE`.

**Para investigar:** pon un breakpoint en [ReplenishmentController.proposals](../../../src/main/java/com/acme/operations/replenishment/ReplenishmentController.java) y sigue los datos hasta PostgreSQL. Después del arreglo puedes repetir las mismas lecturas.

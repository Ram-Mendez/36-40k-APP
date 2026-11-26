# INC-40 · Un lote pierde la vida útil mínima antes de llegar

## Qué tiene que pasar

La respuesta correcta contiene aparecen `L40-B` y `L40-C`.

## Reproduce el fallo

En **Conciliación de almacén**, ejecuta `GET /api/v1/warehouse-reconciliation/eligible-lots/{sku}` con `sku = SKU-40`, `departure = 2026-12-02`, `arrival = 2026-12-05`, `minimumDays = 3`. La respuesta actual muestra aparecen `L40-A`, `L40-B` y `L40-C`.

[
{
"code": "L40-A",
"expiresOn": "2026-12-06",
"units": 4
},
{
"code": "L40-B",
"expiresOn": "2026-12-09",
"units": 5
},
{
"code": "L40-C",
"expiresOn": "2026-12-14",
"units": 8
}
]

## Depura paso a paso

1. Pon el breakpoint en `eligibleLots`. Comprueba que llegan dos fechas distintas y que el plazo mínimo es 3.
   [
   {
   "code": "L40-A",
   "expiresOn": "2026-12-06",
   "units": 4
   },
   {
   "code": "L40-B",
   "expiresOn": "2026-12-09",
   "units": 5
   },
   {
   "code": "L40-C",
   "expiresOn": "2026-12-14",
   "units": 8
   }
   ]



2. Detente tras calcular `minimumExpiry`. El cliente exige que el producto dure tres días completos desde que lo recibe. Calcula el límite a mano. La llegada es el 5 y el límite es el 8 de diciembre.
3. Compara el valor de Java con la fecha de caducidad de `L40-A`, que es el 6. Si Java calcula el 5, investiga de dónde salió esa fecha antes de cambiar el SQL.

[Abre `WarehouseReconciliationService.eligibleLots`](../../../src/main/java/com/acme/operations/reconciliation/WarehouseReconciliationService.java)

## Comprueba tu arreglo

Repite el GET inicial. Después cambia solo `arrival` a `2026-12-06`. `L40-B`, que caduca el 9, debe seguir incluido porque justo cumple el límite.

# INC-35 · El total usa el tamaño de caja del primer producto

Dificultad estimada **6/10**.

El operario prepara dos productos para el mismo traslado. Cada producto tiene su propio número de unidades por caja. Las unidades sueltas se suman al final de cada línea.

1. Ejecuta `POST /api/v1/warehouse-transfers/{code}/preparation/packed-units` con `code = TR-31`.
2. Envía este cuerpo.

```json
{
  "lines": [
    {"boxes": 2, "unitsPerBox": 12, "looseUnits": 3},
    {"boxes": 1, "unitsPerBox": 6, "looseUnits": 1}
  ]
}
```

**Fallo observado.** La API devuelve `totalUnits = 40`.

**Resultado esperado.** La primera línea aporta 27 unidades y la segunda 7. El total es **34**.

Pon un breakpoint en `WarehousePreparationService.packedUnits`. Calcula cada línea por separado y comprueba qué valor usa Java para el tamaño de cada caja.

[WarehousePreparationService.java](../../../src/main/java/com/acme/operations/warehouse/WarehousePreparationService.java)

# INC-50 · La etiqueta de frío toma la ruta general

## Qué tiene que pasar

Una etiqueta puede coincidir con varias reglas. El almacén aplica la regla más específica. La etiqueta `R-04-C-12` pertenece al muelle refrigerado `COLD-BAY-04`.

## Reproduce el fallo

Ejecuta `GET /api/v1/warehouse-controls/routing/{label}` con `label = R-04-C-12`. La API devuelve `prefix = R` y `destination = GENERAL`.

## Depura paso a paso

1. Pon un breakpoint en `WarehouseDailyController.route`. Confirma que el método recibe la etiqueta completa.
2. Lee el WHERE. Para esa etiqueta coinciden `R`, `R-04` y `R-04-C`. Anota qué regla representa cada prefijo.
3. Comprueba qué fila elige la consulta antes de aplicar `LIMIT 1`. Si las tres reglas entran en el filtro, concentra el cambio en cómo se selecciona la primera.

## Comprueba tu arreglo

`R-04-C-12` devuelve `COLD-BAY-04`, `R-04-12` devuelve `BAY-04` y `R-20` devuelve `GENERAL`. La etiqueta `R404` devuelve 404 porque la regla `R` exige que continúe con un guion o termine.

[WarehouseDailyController.java](../../../src/main/java/com/acme/operations/reconciliation/WarehouseDailyController.java)

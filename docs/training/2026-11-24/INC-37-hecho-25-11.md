# INC-37 · La fecha prevista cuenta el sábado como día laborable

Dificultad estimada **6/10**.

El almacén recibe traslados de lunes a viernes. La fecha de salida es el día cero. Con un plazo de cero días, la fecha prevista es la fecha de salida.
lunes 0 martes 1 miercoles 2 jueves 3 viernes 4
si el plazo es 0 - el traslado se hace el mismo dia.

1. Ejecuta `POST /api/v1/warehouse-transfers/{code}/preparation/arrival` con `code = TR-31` y `{"departureDate":"2026-11-27","workingDays":1}`.
2. Comprueba el campo `arrivalDate`.

**Fallo observado.** La API devuelve el sábado `2026-11-28`.
{
"arrivalDate": "2026-11-28"
}
departureDate y workingDays sospechosos

**Resultado esperado.** Con un día laborable devuelve el lunes `2026-11-30`. Con dos días devuelve el martes `2026-12-01`. Con cero devuelve el viernes `2026-11-27`.

Pon un breakpoint en `WarehousePreparationService.arrival`. Fíjate en qué días cuenta Java al calcular la fecha.

[WarehousePreparationService.java](../../../src/main/java/com/acme/operations/warehouse/WarehousePreparationService.java)

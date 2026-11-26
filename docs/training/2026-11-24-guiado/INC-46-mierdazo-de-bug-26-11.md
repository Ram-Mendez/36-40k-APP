# INC-46 · La carga ignora un control pendiente que está dos pasos atrás

## Qué tiene que pasar

La respuesta correcta contiene la respuesta es `[CHECK-46]`.

## Reproduce el fallo

En **Conciliación de almacén**, ejecuta `GET /api/v1/warehouse-reconciliation/blocked-by/{code}` con `code = LOAD-46`. La respuesta actual muestra la respuesta es `[]`.

## Depura paso a paso

1. Ejecuta primero el GET con `code = PACK-46`. La respuesta incluye `CHECK-46`. Así compruebas que la tarea pendiente existe y que la consulta funciona para un requisito directo.
2. Pon el breakpoint en `blockedBy` y ejecuta el GET con `LOAD-46`. Dibuja el camino `LOAD-46 → PACK-46 → CHECK-46`. También existe la rama `LOAD-46 → LABEL-46`.
3. Lee qué filas selecciona el `WHERE d.task = ?`. Señala dónde se recorre el segundo salto. El contrato exige examinar toda la cadena, incluso si una tarea intermedia figura como `DONE`.
4. Antes de corregir, distingue dos pasos. Primero consigue la lista de requisitos alcanzables. Luego filtra los que siguen pendientes.

[Abre `WarehouseReconciliationService.blockedBy`](../../../src/main/java/com/acme/operations/reconciliation/WarehouseReconciliationService.java)

## Comprueba tu arreglo

`LOAD-46` y `PACK-46` deben devolver `[CHECK-46]`. `LABEL-46`, que no tiene requisitos, debe devolver `[]`.

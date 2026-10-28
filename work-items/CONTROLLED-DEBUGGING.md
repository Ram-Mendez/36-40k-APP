# Activación controlada de ejercicios

La versión principal no contiene estos defectos. Activar **un único fixture cada vez**, en una copia de trabajo y base desechables; nunca sobre el Compose usado para datos que se desean conservar. No subir la rama de entrenamiento como versión de producción.

## Preparación

Guardar primero una instantánea versionada propia del repositorio limpio. Desde ella crear un worktree (`git worktree add ..\business-operations-exercises -b training/business-debug`) o una copia independiente. No copiar una base con datos reales. Ejecutar los tests con Testcontainers proporciona una base independiente automáticamente. Para una reproducción manual usar un proyecto Compose distinto, puertos libres y un volumen distinto; cambiar **los tres puertos publicados** evita conectar por accidente a la aplicación principal.

Conservar un diff de cada mutación y ejecutar antes el test de referencia sin ella. Tras el ejercicio deshacer exclusivamente ese diff y comprobar `clean verify`. No aplicar reset sobre trabajo ajeno ni eliminar volúmenes del proyecto principal.

## BOP-104: precio de línea

Solo en la copia, en `OrderRepository.setLine`, cambiar la cláusula SQL `DO UPDATE SET quantity=excluded.quantity` a `DO UPDATE SET quantity=excluded.quantity,unit_price=excluded.unit_price`. Reproducir la secuencia descrita en BOP-104. La corrección del alumno debe volver a preservar precio al modificar cantidad y añadir una prueba que realmente ejecute esa edición tras cambiar tarifa.

## BOP-105: cancelación parcial

Solo en la copia, retirar `@Transactional` de `OrderService.cancel` y cambiar la propagación de `InventoryService.release` de MANDATORY a REQUIRED. Ambas modificaciones son necesarias: MANDATORY protege la versión principal contra una llamada sin transacción. El fixture equivale a fragmentar incorrectamente la cancelación en transacciones por línea. Preparar por API un pedido confirmado con dos productos distintos y stock suficiente. Guardar el estado de saldo/reservas/movimientos. En esa base desechable eliminar deliberadamente la reserva del producto de id mayor:

```sql
-- Sustituir 42 por el pedido exclusivo del ejercicio.
DELETE FROM stock_reservation
WHERE order_id=42 AND product_id=(SELECT max(product_id) FROM stock_reservation WHERE order_id=42);
```

La anomalía previa solo sirve para que la segunda liberación falle de forma determinista. Cancelar por HTTP: la petición debe rechazarse. Comparar con el estado **inmediatamente anterior a cancelar**, no con el anterior a introducir la anomalía. Con la transacción correcta, ninguna primera liberación ni movimiento nuevo sobrevive. La reparación de la anomalía artificial de datos no forma parte del fix; descartar la base aislada al terminar.

## BOP-106: rollback de revocación

Solo en la copia, en `AuthService.refresh`, dentro del bloque `if (session.used())`, sustituir el `return new RefreshResult(null, "REFRESH_REPLAY")` por `throw unauthorized("REFRESH_REPLAY", "Refresh token rejected")`. Mantener el resto igual. Ejecutar el caso de replay de SecurityIntegrationTest: el 401 se mantiene, pero la revocación de la familia queda deshecha por rollback. El ejercicio consiste en reconocer la discrepancia entre respuesta HTTP y estado persistido, sin quitar la protección de replay.

## BOP-113: dependencia lenta

No requiere mutación de código. El test `ShippingClientTest#slowCarrierIsBoundedByReadTimeout` arranca un servidor que retrasa 1 segundo y un cliente con timeout de 200 ms. El escenario HTTP/DB `carrierFailurePreservesConfirmedOrderAndReservationForRetry` muestra el efecto empresarial de un fallo 503 y su recuperación. Para simular una incidencia manual, establecer `$env:CARRIER_MODE='slow'` y ejecutar `docker compose -f compose.yaml -f compose.simulator.yaml up -d --build --force-recreate shipping-service` en el entorno aislado. Consultar logs y reservas, retirar la variable con `Remove-Item Env:CARRIER_MODE`, restaurar el servicio real con `docker compose up -d --build --force-recreate shipping-service` y reintentar el mismo pedido. También existen `unavailable` (503) y `malformed` (JSON inválido), definidos en `infra/carrier/CarrierSimulator.java`.

Los tests de regresión deben fallar con su fixture y pasar al repararlo. No ocultar fallos con `skipTests` ni rebajar aserciones para conseguir un informe verde.

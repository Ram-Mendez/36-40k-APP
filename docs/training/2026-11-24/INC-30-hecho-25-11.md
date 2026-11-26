# INC-30 · Una fila bloqueada detiene toda la cola de preparación

Dificultad estimada **8/10**. Usa las rutas del grupo **Traslados de almacén** en Swagger.

Dos operarios pueden trabajar en la misma cola. Si un operario mantiene bloqueada la primera tarea, el segundo debe poder tomar la siguiente.

1. En la consola PostgreSQL de `lab_20261117`, abre una transacción y ejecuta estas consultas en la misma sesión. Deja esa transacción abierta mientras haces el paso 2.

```sql
BEGIN;
SELECT position
FROM transfer_practice.picking_job
WHERE queue = 'Q-30' AND position = 1
FOR UPDATE;
```

2. En Swagger, ejecuta `POST /api/v1/warehouse-transfers/queues/{queue}/claim-next` con `queue = Q-30`. Ejecuta `ROLLBACK;` en la consola cuando termines.

**Fallo observado.** La petición devuelve `500 INTERNAL_ERROR` al agotar la espera por el bloqueo de la posición 1. La posición 2 sigue disponible.

**Para darlo por resuelto.** Repite ambos pasos con la cola `Q-30-B`. Mientras la posición 1 siga bloqueada, el POST debe devolver 200 con `position = 2` y `status = CLAIMED`. Cierra la transacción de la consola con `ROLLBACK;`.

Pon el primer breakpoint en [`TransferPracticeService.claimNext`](../../../src/main/java/com/acme/operations/warehouse/TransferPracticeService.java).

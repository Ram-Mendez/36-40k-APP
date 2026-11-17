# Validación — segunda tanda del 17/11/2026

**Carpeta única:** `C:\Users\balar\Desktop\36-40k-APP`.

**Base correcta y publicada:** `main`, commit `e741031`. **Rama de ejercicios:** `codex/training-2026-11-17-b`. Esta rama contiene cinco defectos intencionados; sus checks deben empezar en rojo.

## Antes de introducir defectos

- Maven `verify` del núcleo: 52 pruebas unitarias y 105 de integración, sin fallos, errores ni omisiones.
- Shipping `verify`: 26 pruebas, sin fallos, errores ni omisiones.
- Angular: 29 pruebas del almacén, cobertura de formularios 70/70, los dos checks de respuestas tardías del primer lote del 17 y build/copia al backend correctos.
- Nuevos contratos: los cuatro checks Java y los dos de Angular pasaron en la base correcta. Tres checks usan Spring, seguridad y PostgreSQL real de Testcontainers; el de timeout usa HTTP local. Angular usa el servicio de aplicación real con HttpTestingController.
- Se ejecutaron los cuatro preparadores de datos por HTTP real usando Windows PowerShell. Una recepción repetida con líneas reordenadas devolvió el mismo ID en la base correcta.

## Con los cinco defectos activos

Se ejecutó el mismo lanzador entregado al alumno:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\check-current-batch.ps1 -Ticket all
```

| Ticket | Prueba | Resultado preparado |
| --- | --- | --- |
| 01 | receiptRetryWithReorderedLinesKeepsTheSameReceiptAndStock | Falla: reordenar líneas devuelve 409 en vez de 200 |
| 02 | logoutOnlyRevokesItsOwnLoginFamily | Falla: la segunda sesión obtiene 401 en vez de 200 |
| 03 | salesReportCountsOrdersRatherThanLinesEvenWhenTotalsAreEqual | Falla: cuenta tres pedidos en vez de dos |
| 04 | CurrentBatchTimeoutTest | El futuro excede el presupuesto de 1,5 s: el cliente no respeta los 250 ms de lectura configurados en el test |
| 05 | collect every page including a short final page | Falla: Angular no solicita la última página; el control de listas vacías permanece verde |

El lanzador terminó con código 1. Surefire contabiliza tres fallos de aserción y una `TimeoutException` en cuatro casos Java; esta excepción es la evidencia esperada del 04, no un fallo de compilación o arranque. Angular contabiliza un fallo y un control verde. Ninguna prueba fue omitida. No se relajaron aserciones ni se añadió la solución a los tickets.

Después de introducir los defectos, también pasó `npm run build:backend`, que actualizó el frontend servido por Spring.

## Reproducciones por HTTP real

Se lanzaron las clases Main con Java 21 usando los argumentos guardados en `Backend APP` y `Shipping APP`. Se usó la base de desarrollo existente del 17, conservando sus datos. La validación no pulsó Debug en IntelliJ.

Evidencia capturada: [HTTP-EVIDENCE.json](HTTP-EVIDENCE.json).

- Backend readiness UP y Swagger 200.
- 01: recepción inicial 200; reintento con las mismas líneas reordenadas 409 `IDEMPOTENCY_CONFLICT`.
- 02: dos logins independientes de una cuenta de prueba nueva; B obtiene 200 antes de cerrar A y 401 después del logout de A. Se evitó cerrar sesiones de las cuentas usadas habitualmente por el alumno.
- 03: cliente preparado con dos pedidos de 30 €; el informe muestra tres pedidos y 90 €.
- 04: sustituto de Shipping accesible en 39098 con respuesta tras 1,5 s; Backend acepta y completa el pedido en 1617 ms pese a tener lectura configurada en 1000 ms.
- 05: la API devuelve 15 elementos en su segunda página, de un catálogo de 115 productos preparado para la prueba. La omisión de esa página por Angular está comprobada por el test del servicio; no se certificó aceptación visual de navegador.

Se detuvieron los procesos Java y el simulador usados para verificar, dejando libres los puertos para IntelliJ. PostgreSQL queda disponible en 35438. La base contiene datos de preparación: los IDs y cuerpos se guardaron en `target/current-batch` y el entorno privado del cliente HTTP.

## Alcance

La suite completa se ejecutó en la base reparada, antes de los cinco defectos. En la rama de entrenamiento se ejecutaron los checks específicos y la compilación del frontend, no la suite completa. Los logs e informes actuales están en `current-base-java.log`, `current-base-ui.log`, `current-defects.log`, `current-ui-build.log` y `target/surefire-reports`.

La reducción a una única copia, los commits anteriores publicados y las rutas de los respaldos se describen en [CONSOLIDATION-2026-11-17.md](../CONSOLIDATION-2026-11-17.md). Las ramas históricas siguen disponibles en GitHub; no es necesario crear worktrees para usarlas.

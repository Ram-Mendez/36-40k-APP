# Validación — 17/11/2026

## Base y alcance

Se creó una copia independiente desde `c2ccebd` y se aplicaron las modificaciones locales del 16/11, sin cambiar aquella carpeta. El commit `02f1c29` conserva la base corregida, incluidas las notas y el proxy del usuario.

Antes de los nuevos defectos pasaron 28 comprobaciones del núcleo del batch 16 y 24 del servicio de envíos: 52 en total, sin fallos. Esto comprueba las correcciones del 16; no certifica toda la suite histórica de integración.

## Nuevos escenarios

Se ejecutaron los checks de los nuevos contratos antes y después de introducir los defectos.

| Ticket | Comprobación | Antes | Después |
| --- | --- | --- | --- |
| 01 | `Batch17QuoteHistoryTest` | 2 pasan | 2 fallan por cotización obsoleta |
| 02 | `BusinessIntegrationTest#staleProductUpdateIsConflictAndPreservesNewPrice` | Pasa | Falla: edición antigua recibe 200 en vez de 409 |
| 03 | `SecurityIntegrationTest#tokenRotationRejectsOldAccessAndReplayRevokesSuccessor` | Pasa | Falla: sucesor sigue autorizado después del replay |
| 04 | `Batch17RecoveryTest` | Pasa | El segundo intento lanza BusinessException aunque el servidor ya devuelve 200 |
| 05 | `frontend/scripts/test-batch17.mjs` | 2 pasan | Falla la respuesta tardía; pasa el control de la carga actual |

Son siete comprobaciones: las siete pasan en la base; seis detectan los cinco defectos y una permanece verde. Surefire clasifica la excepción del 04 como un error de test; es el comportamiento de negocio preparado, no un error de arranque. Los tickets 02 y 03 se ejecutaron con el contexto Spring, filtros reales y PostgreSQL desechable de Testcontainers. El 04 usa HTTP real contra un servidor local que cambia de 503 a 200. El 05 controla el orden de resolución con el servicio Angular real y HttpTestingController.

También pasó `npm run build`. Las configuraciones Application y el Compose del 17 usan puertos separados: Backend 38088, Shipping 39098, PostgreSQL 35438 y frontend 4201. El proxy de retraso del ejercicio 05 usa 38090; su sintaxis se verificó con Node.

Los logs de preparación se conservan localmente como `baseline17-*.log`, `batch17-base-*.log`, `batch17-defect-*.log` y `batch17-build-ui.log` (ignorados por Git). Los XML de Surefire contienen los resultados actuales. Los tests de esta rama empiezan deliberadamente en rojo.

No se ejecutó la suite completa del repositorio, Jenkins ni aceptación visual en navegador. La carrera de la interfaz está comprobada automáticamente; la secuencia manual con el proxy queda documentada para el alumno. No se alteraron las migraciones ni se aplicaron los nuevos defectos al proyecto anterior.

La estimación 4/5 describe la investigación necesaria para este usuario; no es una escala objetiva ni una garantía de tiempo. El arreglo final puede ocupar pocas líneas.

## Arranque real

Se arrancó el Compose del 17 y ambas clases Main con Java 21 y los argumentos leídos de sus archivos `.run`. Ambos readiness dieron UP, Swagger devolvió 200 y se reprodujo el 01 por HTTP real con un ID reservado para validación. Resultado: [runtime-check.json](runtime-check.json).

La validación se hizo desde Java, no pulsando Debug en IntelliJ. Al terminar se detuvieron esos dos procesos para liberar 38088 y 39098; PostgreSQL permanece listo en 35438. El lanzador PowerShell se verificó sintácticamente, y sus comandos Maven/Node se ejecutaron por separado durante las comprobaciones anteriores.

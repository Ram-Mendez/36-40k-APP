# Una app: 36-40k-APP

Abre siempre `C:\Users\balar\Desktop\36-40k-APP` en IntelliJ. El trabajo guardado está en GitHub; ya no necesitas las carpetas BATCH ni DEBUG.

## Arranque habitual

En Docker Desktop, inicia `business-operations-batch17` si está detenido.

En IntelliJ ejecuta **Shipping APP** y **Backend APP** con Debug. Las dos configuraciones Application ya incluyen Java 21, clase Main, módulo y Program arguments. No cambian con cada tanda.

- Backend y Swagger: http://localhost:38088/swagger-ui/index.html
- Shipping: http://localhost:39098/actuator/health/readiness
- Frontend: desde `frontend`, ejecuta `npm start`; abre http://localhost:4200.
- Login de desarrollo: `admin`, `operations` o `readonly`; contraseña `DevPassword123!`.

Si necesitas revisar Program arguments, están en `.run/Backend APP.run.xml` y `.run/Shipping APP.run.xml`. IntelliJ los muestra en **Run → Edit Configurations**. PostgreSQL mantiene tus datos en 35690.

## Tu jornada de backend Java

De lunes a viernes, Jenkins mide la actividad a las **08:30** y Codex envía el parte por Gmail a las **08:45**, hora de Madrid. Para la lectura y el envío programados, el equipo debe estar despierto con Docker Desktop y Codex en ejecución.

1. Abre el correo **[36-40k-APP] Parte backend**. Verás la actividad de las últimas 24 horas, el estado del backend y las incidencias detectadas en los datos guardados.
2. Si hay una incidencia, comprueba el identificador y los importes o cantidades del correo en [Swagger](http://localhost:38088/swagger-ui/index.html). Reproduce el fallo en IntelliJ y verifica el arreglo con la misma operación sobre datos nuevos.
3. Para consultar las mediciones o repetirlas, entra en [Jenkins · parte-backend](http://127.0.0.1:8082/job/parte-backend/) con la cuenta `backend-java`. Cada ejecución guarda `informe.txt` como artefacto.

## Tanda actual

**Turno Cero · 10 incidentes de Spring Boot.** Ejecuta `powershell -ExecutionPolicy Bypass -File .\scripts\turno-cero.ps1` y arranca **Turno Cero** en Debug. Para reproducir el primer fallo, ejecuta `powershell -ExecutionPolicy Bypass -File .\scripts\comprobar-turno-cero.ps1 -Caso 1`.

## Tanda anterior

[22/11 · 10 incidencias de Java y PostgreSQL](docs/training/2026-11-22/TICKETS.md).

Empieza por [INC-01 · Un pedido pierde una línea](docs/training/2026-11-22/INC-01.md). Los datos están preparados en tu base habitual. Usa Swagger para reproducir la incidencia y el depurador de IntelliJ para seguir el código del backend.

[Rama de práctica del 17/11](https://github.com/Ram-Mendez/36-40k-APP/tree/codex/training-2026-11-17-b) · [Trabajo guardado y validación anterior](docs/training/CONSOLIDATION-2026-11-17.md).

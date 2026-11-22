# Una app: 36-40k-APP

Abre siempre `C:\Users\balar\Desktop\36-40k-APP` en IntelliJ. El trabajo guardado está en GitHub; ya no necesitas las carpetas BATCH ni DEBUG.

## Tanda actual · SQL 51–60

Abre [los ejercicios 51–60 de Java y SQL](docs/training/sql-guiado/51-60/EMPIEZA.md) en la consola de `lab_20261117@localhost`, puerto `35438`. El primer caso investiga una búsqueda de productos por SKU.

## SQL 21–30

Abre [los casos 21–30](docs/training/sql-guiado/21-30/EMPIEZA.md) en la conexión `lab_20261117@localhost`, puerto `35438`. Empieza por el informe de unidades pendientes.

## Tanda anterior · SQL 15–20

Abre [los casos 15–20](docs/training/sql-guiado/11-20/EMPIEZA.md) en la conexión `lab_20261117@localhost`, puerto `35438`. Cada caso indica qué debe salir, qué sale y qué parte del SQL revisar.

## Primera tanda · SQL 01–10

Abre [la guía de la tanda](docs/training/sql-guiado/EMPIEZA.md) y empieza por el caso de Eva. Se usa la consola SQL de `lab_20261117` en el puerto `35438`. La guía incluye una práctica con el depurador de Spring Boot.

## Arranque habitual

```powershell
docker compose -f compose.batch17.yaml up -d --wait
```

En IntelliJ ejecuta **Shipping APP** y **Backend APP** con Debug. Las dos configuraciones Application ya incluyen Java 21, clase Main, módulo y Program arguments. No cambian con cada tanda.

- Backend y Swagger: http://localhost:38088/swagger-ui/index.html
- Shipping: http://localhost:39098/actuator/health/readiness
- Frontend: desde `frontend`, ejecuta `npm start`; abre http://localhost:4200.
- Login de desarrollo: `admin`, `operations` o `readonly`; contraseña `DevPassword123!`.

Si necesitas revisar Program arguments, están en `.run/Backend APP.run.xml` y `.run/Shipping APP.run.xml`; IntelliJ los muestra en **Run → Edit Configurations**. PostgreSQL mantiene tus datos en 35438.

## Tanda anterior · 17/11

[Ejercicios del 17/11 — segunda tanda](docs/training/2026-11-17-b/TICKETS.md).

Rama preparada: `codex/training-2026-11-17-b`. [Pruebas y reproducciones](docs/training/2026-11-17-b/VALIDATION.md).

La carpeta y las configuraciones se mantienen. `main` conserva la base correcta; la rama de práctica tiene los cinco defectos intencionados. Para cambiar de rama, detén primero las aplicaciones.

[Trabajo guardado, copias y validación](docs/training/CONSOLIDATION-2026-11-17.md).

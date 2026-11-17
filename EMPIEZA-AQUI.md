# Una app: 36-40k-APP

Abre siempre `C:\Users\balar\Desktop\36-40k-APP` en IntelliJ. El trabajo guardado está en GitHub; ya no necesitas las carpetas BATCH ni DEBUG.

## Arranque habitual

```powershell
docker compose -f compose.lab.yaml up -d --wait
```

En IntelliJ ejecuta **Shipping APP** y **Backend APP** con Debug. Las dos configuraciones Application ya incluyen Java 21, clase Main, módulo y Program arguments. No cambian con cada tanda.

- Backend y Swagger: http://localhost:38088/swagger-ui/index.html
- Shipping: http://localhost:39098/actuator/health/readiness
- Frontend: desde `frontend`, ejecuta `npm start`; abre http://localhost:4200.
- Login de desarrollo: `admin`, `operations` o `readonly`; contraseña `DevPassword123!`.

Si necesitas revisar Program arguments, están en `.run/Backend APP.run.xml` y `.run/Shipping APP.run.xml`; IntelliJ los muestra en **Run → Edit Configurations**. PostgreSQL mantiene tus datos en 35438.

## Tanda actual

[Ejercicios del 17/11 — segunda tanda](docs/training/2026-11-17-b/TICKETS.md).

Rama preparada: `codex/training-2026-11-17-b`. [Pruebas y reproducciones](docs/training/2026-11-17-b/VALIDATION.md).

La carpeta y las configuraciones se mantienen. `main` conserva la base correcta; la rama de práctica tiene los cinco defectos intencionados. Para cambiar de rama, detén primero las aplicaciones.

[Trabajo guardado, copias y validación](docs/training/CONSOLIDATION-2026-11-17.md).

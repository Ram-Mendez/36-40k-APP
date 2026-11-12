# Evidencia de publicación — 12/11/2026
**Estado:** `PUBLISHED_UNVALIDATED`. **Base:** `ad23c49900c51202f8c3c965e5b153d9e925990d`. **Incidents code SHA:** `5c40fc0c98b36dfd52c02b7f73fd0e80fdbd5a69`.

| Comprobación | Estado | Fuente |
|---|---|---|
| Base ampliada seleccionada | PASS (histórico) | Informe de 10/11 para este mismo SHA; no se volvió a ejecutar |
| Cinco archivos Java modificados en incidents | PASS | GitHub compare confirmó `ahead=1`, cinco rutas |
| Team A y B tienen cambios Java | PASS | GitHub compare confirmó `ahead=1` y una ruta en cada una |
| Compilación Maven núcleo y shipping | NOT_EXECUTED | No hay checkout con dependencias Maven en el runtime de este turno |
| Arranque Spring/Flyway y PostgreSQL real | NOT_EXECUTED | Sin servicios de pruebas iniciados |
| Cinco escenarios HTTP y SQL | NOT_EXECUTED | Reproducciones solo diseñadas |
| Carrera de cobros coordinada | NOT_EXECUTED | Falta entorno y ejecución concurrente real |
| Independencia, regresiones y compatibilidad | NOT_EXECUTED | Requiere ejecutar tests y fronteras |
| Email del 12/11 | PENDIENTE | Confirmación separada tras publicación |

**Comandos orientativos** sobre un worktree y Docker/PostgreSQL independientes, JDK 21:
```powershell
.\mvnw.cmd -B -ntp clean verify
.\mvnw.cmd -B -ntp -f services/shipping-service/pom.xml clean verify
```
Los cinco defectos están introducidos a propósito; pruebas de regresión pueden fallar como se espera, sin que eso equivalga a un PASS. No se ha modificado ni desactivado ningún test ni migración. No se ha lanzado Jenkins/Kind. Nunca declarar cinco bugs verificados con solo un `git diff` o un build exitoso.

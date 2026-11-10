# Manifiesto del lote — 2026-11-11

**Estado:** `PUBLISHED_UNVALIDATED`. Cinco alteraciones de código publicadas, pero **sin pruebas Maven/HTTP/PostgreSQL ejecutadas en esta entrega**.

## Identificación y alcance

- Repositorio: `Ram-Mendez/36-40k-APP`.
- Base exacta: `ad23c49900c51202f8c3c965e5b153d9e925990d` (`main` en lectura anterior a publicar); ampliación única del 10/11; [evidencia de base](https://github.com/Ram-Mendez/36-40k-APP/blob/ad23c49900c51202f8c3c965e5b153d9e925990d/docs/expansion/2026-11-10/VALIDATION.md).
- Aplicación: Java 21 / Spring Boot 3.5.12 / PostgreSQL 16, núcleo más `services/shipping-service`.
- Rama de incidencias: `training/2026-11-11/incidents`, último commit de código `5d8b51a2c8056892288aa32975af203b487fc221`; ticket publicado en `6b631bdd5f44f5fb9177eba220586785c69ecaba`. El HEAD remoto actual se determina mediante GitHub porque los propios documentos añaden commits.
- Rama compañero A: `training/2026-11-11/team-a` @ `56dd80223ded8da434b4dc4c85324e06a4658e32`.
- Rama compañero B: `training/2026-11-11/team-b` @ `37d369b25de1e70a448e7d0b1779cc285d540db9`.
- Tickets: `LAB-20261111-01` a `LAB-20261111-05`, `FEATURE-20261111-01`, `GIT-20261111-01`, todos en [TICKETS.md](TICKETS.md).
- Contratos de compañeros: A, consulta por email de proveedor; B, asientos contables ordenados por incorporación más reciente.
- No se ha modificado `main`, sus etiquetas anteriores, migraciones, tests, assertions, shipping ni equipos locales.

## Recuperación segura en PowerShell

Desde el checkout existente, sin sobrescribir ramas ni carpetas locales:

```powershell
git status --short
git fetch origin
git branch -r --list "origin/training/2026-11-11/*"
git worktree add ..\lab-2026-11-11 -b practice/2026-11-11 origin/training/2026-11-11/incidents
```

El comando `worktree add` requiere que **esa carpeta y esa rama local sean nuevas**; si existen, elige nombres distintos. La operación no toca los cambios sin commit del checkout original. Para recibir los compañeros, consulta los refs remotos `origin/training/2026-11-11/team-a` y `origin/training/2026-11-11/team-b`. La integración debe realizarse en una tercera rama personal, **no en `main` ni en las ramas remotas recibidas**. No hay comandos de solución Git en los tickets.

En el worktree de incidencias, ejecutar Maven Wrapper requiere JDK 21 y Docker/Testcontainers o una PostgreSQL de pruebas dedicada; no apuntar a la base de trabajo. La aplicación de práctica no debe publicar los puertos del entorno habitual `8086/9096/5436`. Si se usan servicios temporales, cambiar `SERVER_PORT`, `SHIPPING_BASE_URL`, puerto de PostgreSQL y proyecto Compose en un entorno aislado antes de arrancar.

## Evidencia real disponible

El conector GitHub confirmó para `incidents` **5 archivos Java y 1 documento** modificados frente al mismo SHA de base (6 commits de ventaja, ninguno por detrás al comparar tras publicar tickets). A y B contienen una modificación de Java y un commit cada una frente a esa misma base; las dos comparaciones devolvieron `status=ahead`, sin historial ajeno agregado. Las alteraciones fueron releídas en sus ramas tras cada escritura.

No se ha certificado compilación, arranque, reproducción de síntomas ni independencia en runtime. Ver [VALIDATION.md](VALIDATION.md). Esta limitación impide declarar verificadas las cinco incidencias, pero no invalida la existencia de los cambios publicados.

## Referencias para evaluación (sin revelar soluciones)

- Contratos históricos: `training/control/docs/training/SCENARIO-LEDGER.json` y `docs/training/2026-11-10/TICKETS.md`. El registro operativo y criterios de novedad se mantienen **fuera** de estos tickets.
- Documentación de negocio ampliado: [API.md](https://github.com/Ram-Mendez/36-40k-APP/blob/ad23c49900c51202f8c3c965e5b153d9e925990d/docs/expansion/2026-11-10/API.md).
- [Spring Boot 3.5: Testcontainers](https://docs.spring.io/spring-boot/3.5/reference/testing/testcontainers.html); [PostgreSQL 16: aislamiento](https://www.postgresql.org/docs/16/transaction-iso.html); [GitHub: pull requests y checks](https://docs.github.com/en/pull-requests/reference/pull-requests).

**No se infiere que el usuario haya completado ningún ticket anterior.**

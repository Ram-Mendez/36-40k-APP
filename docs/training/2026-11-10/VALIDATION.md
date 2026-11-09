# Validación · laboratorio 2026-11-10

**Base:** `a6bc8058415a21c3dc75564974e375e69bdab9a9`. **Código de incidencias:** `bcfd7035f0b82473649a3e11e33802122819066a`. **Estado de la entrega:** `UNVALIDATED` en tiempo de ejecución; no afirmar cinco defectos reproducidos hasta disponer de resultados HTTP/SQL.

| Comprobación | Estado | Evidencia |
|---|---|---|
| Publicación del código de incidencias | PASS | GitHub confirmó nuevo commit y ref; comparación contra la base: cinco archivos Java modificados, sin tests ni migraciones modificados. |
| Publicación de team-a y team-b | PASS | GitHub confirmó refs y commits `4a3f376b0aada3803471161301176f83b6b75070` y `2cd1fbd9354d0193ff09b8007496a56ddef2c89a`; cada uno modifica ProductService y un documento propio. |
| Revisión estática de sustituciones | PASS | Se comprobó que cada sustitución textual afectaba exactamente una coincidencia antes de crear el commit; no sustituye la compilación Java ni una prueba de negocio. |
| Baseline: Maven `./mvnw.cmd -B -ntp clean verify` | NOT_EXECUTED | No hay checkout con dependencias ni ejecución de Java/Docker accesible desde la conexión GitHub. |
| Compilación: `./mvnw.cmd -B -ntp -DskipTests package` | NOT_EXECUTED | No se ejecutó JVM ni Maven sobre la rama. |
| Integración: `./mvnw.cmd -B -ntp verify` con Testcontainers PostgreSQL | NOT_EXECUTED | No hay base PostgreSQL/daemon Docker conectado para verificar HTTP y persistencia. No sustituir por H2. |
| LAB-20261110-01 | NOT_EXECUTED | Reproducción pendiente. |
| LAB-20261110-02 | NOT_EXECUTED | Reproducción pendiente. |
| LAB-20261110-03 | NOT_EXECUTED | Reproducción pendiente. |
| LAB-20261110-04 | NOT_EXECUTED | Reproducción pendiente. |
| LAB-20261110-05 | NOT_EXECUTED | Reproducción pendiente. |
| Verificación de CI Jenkins + Kind | NOT_EXECUTED | No se disparó el pipeline completo en ninguna rama. |
| Comprobación de ausencia de efectos colaterales | NOT_EXECUTED | Requiere entorno aislado con datos y tests. |

**Procedimiento verificable cuando exista entorno:** crear un worktree nuevo según `MANIFEST.md`, arrancar una instancia aislada con puertos/Compose/BD propios, ejecutar ambas suites de tests y reproducir cada ticket, registrando status HTTP, estado persistido y requestId. Registrar los fallos intencionados como FAIL_EXPECTED y cualquier otro fallo como FAIL_UNEXPECTED, sin falsear el éxito de la suite.

**Fuentes de ingeniería de referencia:** [Spring Boot Testcontainers](https://docs.spring.io/spring-boot/reference/testing/testcontainers.html), [PostgreSQL 16 — isolation](https://www.postgresql.org/docs/16/transaction-iso.html), [OWASP API Security 2023](https://owasp.org/API-Security/editions/2023/en/0x11-t10/). Son documentación técnica, no pruebas de ejecución ni justificación para desplegar versiones antiguas sin mantenimiento. El registro de proyectos reales está en `training/control`.

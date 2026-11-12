# Validación y limitaciones · 13/11/2026

**Estado:** `PUBLISHED_UNVALIDATED`. **Base:** `ad23c49900c51202f8c3c965e5b153d9e925990d`. **Incidents código:** `5b354475c8ce3b777c6272404f812a71395fca80`. Comprobaciones reproducibles y honestas:

| Comprobación | Resultado | Evidencia / acción |
|---|---|---|
| Base ampliada validada anteriormente | PASS (histórico del MISMO SHA) | [checkpoint 10/11](https://github.com/Ram-Mendez/36-40k-APP/blob/ad23c49900c51202f8c3c965e5b153d9e925990d/docs/expansion/2026-11-10/VALIDATION.md) |
| 5 archivos Java modificados en incidents | PASS (publicación de código) | commit `5b354475c8ce3b777c6272404f812a71395fca80`; comparar con base |
| Cambios reales en team-a y team-b | PASS (publicación de código) | commits `8e2e918fcb5bce22523fcc2d327432d45c4d44d8` y `0308a53693c2ccaecd9238eca773726058fb2fa6` |
| JDK del entorno de asistencia | PASS | `java -version` devolvió OpenJDK 21.0.12.1 |
| Compilación del lote con Maven | NOT_EXECUTED | `mvn` no existe en el entorno de asistencia; sin acceso de red a GitHub/dependencias Maven |
| Arranque Spring Boot/Flyway con PostgreSQL 16 | NOT_EXECUTED | Sin servidor PostgreSQL ni Docker accesibles; sin tocar contenedores del usuario |
| Reproducción de cinco escenarios | NOT_EXECUTED | Diseñados en TICKETS; no se han ejecutado peticiones ni SELECT de verificación |
| Pruebas de integración con PostgreSQL | NOT_EXECUTED | Necesita entorno de pruebas aislado; nunca se sustituyó por H2 |
| Regresión e independencia de cinco defectos | NOT_EXECUTED | No inferir de compilación o inspección estática |
| Merge Git y tests de aceptación A/B | NOT_EXECUTED | Se reservan para el desarrollador |
| Email notificatorio | PASS | Gmail `send_email` devolvió `1a1d21679a041eb3`; lectura posterior confirmó destinatario `rammendez95@gmail.com`, asunto exacto `Laboratorio backend 36–40k — 2026-11-13` y etiqueta `SENT` |

**Comandos de validación** para worktree separado, desde raíz del repositorio (PowerShell, con dependencias disponibles):

```powershell
.\mvnw.cmd -B -ntp -DskipTests package
.\mvnw.cmd -B -ntp verify
.\mvnw.cmd -B -ntp -f services/shipping-service/pom.xml verify
```

El código incluye fallos deliberados y podría producir pruebas fallidas; clasificar `FAIL_EXPECTED` solo cuando exista evidencia de fallo que corresponda al contrato, sin silenciar pruebas. Se requiere PostgreSQL real para conclusiones de persistencia. No se lanzaron pipelines Jenkins/Kind.

**Fuentes técnicas contrastadas el 13/11/2026:** [Spring Framework 6.2 @Transactional](https://docs.spring.io/spring/reference/6.2/data-access/transaction/declarative/annotations.html), [PostgreSQL 16 locks](https://www.postgresql.org/docs/16/explicit-locking.html), [GitHub REST refs](https://docs.github.com/en/rest/git/refs), [OWASP API Security 2023](https://owasp.org/projects/api-security-project). Versiones compatibles con Java 21 y el stack observado; no se cambió versión. Corpus mantenido en 3 repositorios previamente acreditados; no se inspeccionaron productos nuevos en esta ejecución.

# Decisiones de mantenimiento basadas en fuentes actuales · 2026-11-10

**Inventario propio observado:** proyecto en `main` sobre Java 21, Spring Boot **3.5.12**, Maven y PostgreSQL 16, con un núcleo de operaciones y servicio de cotizaciones. No se ha ejecutado aquí la suite ni el arranque.

**Aviso de soporte prioritario:** Spring publicó [3.5.16 el 25/06/2026](https://spring.io/blog/2026/06/25/spring-boot-3-5-16-available-now/) como **última versión OSS de la línea 3.5.x**, y recomendó pasar a 4.0.x/4.1.x o contratar mantenimiento comercial. Mantener 3.5.12 sin plan de soporte no equivale a disponer de parches recientes; es una deuda de mantenimiento visible a nivel de ingeniería. **No actualizar la rama de bugs ni modificar `main` automáticamente**: plantear rama de mantenimiento aislada, inventario de dependencias, compatibilidad de Security/Spring Framework, compilación, migraciones, tests y marcha atrás antes de proponer integración.

**Persistencia:** usar [aislamiento PostgreSQL 16](https://www.postgresql.org/docs/16/transaction-iso.html) y `SELECT ... FOR UPDATE` con criterio; la elección de aislamiento no sustituye pruebas de concurrencia. Validar `JdbcTemplate`, parámetros SQL, límites y paginación sobre PostgreSQL auténtico.

**Pruebas:** [Spring Boot Testcontainers](https://docs.spring.io/spring-boot/reference/testing/testcontainers.html) valida servicios dependientes reales mediante contenedores; mantener unitarias, integración y regresión en clases separadas, ejecutadas realmente cuando exista Docker. No presentar suites no ejecutadas como aprobadas.

**Seguridad:** [OWASP API Security Top 10 2023](https://api-security.owasp.org/editions/2023/en/0x11-t10/) ofrece marco de fallos de autenticación, autorización y consumo inseguro de APIs. No desplegar ramas con regresiones intencionadas; no usar secretos reales.

**Alcance:** se registran decisiones relevantes de fuentes oficiales; no se ha auditado un millar de repositorios ni aplicado un estándar universal obligatorio de 2026. Mantenimientos e integraciones requieren justificación y validación individual.

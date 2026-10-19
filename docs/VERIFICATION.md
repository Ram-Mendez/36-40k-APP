# Verificación de la entrega

Fecha: **19 de octubre de 2026**, Windows 11, zona Europe/Madrid. Pruebas ejecutadas sobre los archivos entregados; no son resultados previstos ni ejemplos de salida.

## Build y suite automatizada

Comando ejecutado desde PowerShell con Eclipse Temurin **21.0.10** y Maven Wrapper **3.9.12**:

```powershell
.\mvnw.cmd -B -ntp clean verify
```

Resultado final: **BUILD SUCCESS**, 04:34:25 +02:00, 1 min 16 s. **75 pruebas, 0 fallos, 0 errores, 0 omitidas**.

| Suite | Pruebas |
| --- | ---: |
| InventoryServiceTest | 10 |
| order.OrderServiceTest | 4 |
| OrderServiceTest | 5 |
| ShippingClientTest | 8 |
| BusinessIntegrationTest | 30 |
| BootstrapAdminIntegrationTest | 2 |
| PermissionAndErrorIntegrationTest | 5 |
| SecurityIntegrationTest | 11 |
| **Total** | **75** |

Surefire: 27 pruebas. Failsafe: 48 integraciones sobre PostgreSQL **16.13** real, creado por Testcontainers. Incluyen tres repeticiones de confirmaciones concurrentes, carrera de refresh, rollback de varias líneas y cancelación mientras una cotización HTTP está bloqueada mediante latches. Se verifica el estado persistido de saldos, reservas, movimientos, pedidos y sesiones.

La matriz READONLY comprueba 18 operaciones denegadas y que no cambian tablas de negocio. El cliente de transporte se prueba con servidor HTTP real local, timeout, 503, JSON inválido y cotizaciones incorrectas. Las pruebas de errores HTTP detectaron una prioridad incorrecta del advice frente al manejador de Spring; quedó corregida y las respuestas 400/405/415 incluyen el contrato común.

JaCoCo: 1003 de 1155 líneas cubiertas (86,84 %) y 185 de 293 ramas (63,14 %). Es evidencia orientativa, no garantía de ausencia de defectos. Informes regenerables en `target/surefire-reports`, `target/failsafe-reports` y `target/site/jacoco`.

## Docker, migraciones y datos

Ejecutado `docker compose up -d --build --wait`; construido el backend desde fuentes dentro de la imagen. Los tres servicios quedaron **healthy**. Java del contenedor: Temurin **21.0.12.1**. Motor de datos: PostgreSQL **16.13**. Las versiones menores del JDK difieren del host, ambas son Java 21.

Consulta directa de `flyway_schema_history`: V1 catálogo/auditoría, V2 seguridad, V3 pedidos/inventario y V4 datos de desarrollo, todas con `success=true`. Hibernate arranca con `ddl-auto=validate`. El seed creó 3 usuarios, 3 clientes, 8 productos, saldos y movimientos iniciales y 1 pedido DRAFT. El smoke añade sus propios datos identificables; por ello los recuentos del entorno entregado son mayores que los iniciales.

Se reconstruyó y recreó el contenedor de aplicación conservando el volumen PostgreSQL: arranque correcto, migraciones validadas y datos conservados. No se borraron los datos de desarrollo.

## Flujo HTTP en la aplicación arrancada

Ejecutado dos veces:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\smoke.ps1
```

Último resultado: **PASS**, requestId `smoke-1f1a615ae96b`, pedido completado **4**, pedido cancelado **5**, producto **10**. El escenario crea cliente/producto, añade 10 unidades a 19,95 EUR, solicita 3 unidades y comprueba total 59,85 EUR. Confirmar deja 3 reservadas y 7 disponibles; completar deja 7 físicas y 0 reservadas. Un segundo pedido de 2 unidades se confirma y cancela sin cambiar las 7 físicas. Resultado: cinco movimientos ADJUSTMENT/RESERVE/SHIP/RESERVE/RELEASE.

También se comprueban 401 sin autenticación, 403 para escritura READONLY, 409 al repetir finalización, renovación del par de tokens, rechazo del access anterior, logout, auditoría correlacionada, readiness UP, Swagger HTTP 200 y existencia del contrato de confirmación en OpenAPI. El documento OpenAPI publicado contiene **34 operaciones**. El simulador de transporte recibe el mismo X-Request-ID; se verificó en sus logs.

SQL adicional confirmó en los pedidos 2/3 del primer smoke estados COMPLETED/CANCELLED y saldo físico 7/reservado 0. No se basó la comprobación únicamente en respuestas HTTP.

## Arranque sin datos de desarrollo

Se creó una base local independiente `operations_production_check` y se arrancó temporalmente la imagen sin perfiles dev/test, con secretos de prueba generados y bootstrap explícito. Verificado: únicamente migraciones V1–V3, un ADMIN `initialadmin`, login HTTP 200, catálogo vacío y OpenAPI anónimo HTTP 401. El contenedor temporal se detuvo y eliminó al concluir; la base aislada de comprobación se conserva en el PostgreSQL local. No contiene información empresarial real.

## Alcance y límites de la evidencia

- GitHub Actions está configurado, pero no se ha publicado el repositorio ni ejecutado el workflow en GitHub. La compilación, suite y construcción de imagen se ejecutaron localmente.
- El proveedor de transporte es el simulador del contrato; no existe integración con una empresa transportista real.
- No se han hecho pruebas de carga, despliegue cloud, TLS público ni restauración de backups. No se afirma que estas comprobaciones estén realizadas.
- IntelliJ tiene instrucciones exactas en README; no se verificó mediante automatización de su interfaz gráfica.
- El entorno de desarrollo queda iniciado en puertos 8086, 5436 y 9096. Detenerlo con `docker compose stop` conserva sus datos.
- Las decisiones de producto y extensiones deliberadamente pendientes están en `08-ENGINEERING-DECISIONS.md` y en los 15 tickets. No se han introducido fallos deliberados en la versión principal.

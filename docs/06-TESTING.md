# Pruebas y criterios de calidad

Usar JDK 21 y Docker Desktop con contenedores Linux. No es necesario arrancar el Compose de la aplicación para ejecutar la suite: Testcontainers crea su propio PostgreSQL 16 con puerto aleatorio y Flyway construye el esquema.

```powershell
.\mvnw.cmd clean verify
# Solo pruebas rápidas, incluidos clientes HTTP con servidor local:
.\mvnw.cmd test
# Selección de integración; mantener el objetivo verify:
.\mvnw.cmd "-Dit.test=BusinessIntegrationTest" verify
```

Surefire ejecuta `*Test` excluyendo `*IntegrationTest`; Failsafe ejecuta integración en integration-test/verify y propaga los fallos. No usar `-DskipTests` como evidencia de calidad. Los XML y logs están en `target/surefire-reports` y `target/failsafe-reports`. JaCoCo genera `target/site/jacoco/index.html` al verificar; el porcentaje es orientación, no prueba de cumplimiento de reglas.

## Cobertura por riesgo

| Prueba | Riesgo y evidencia |
| --- | --- |
| InventoryServiceTest | Límites y cero rechazados antes de escribir; auditoría solo tras ajuste correcto. |
| OrderServiceTest | Estados inválidos, pedido vacío y versión de cotización antigua no consumen stock. |
| ShippingClientTest | HTTP real local: contrato válido, error 503, JSON inválido, importes incorrectos y timeout. |
| SecurityIntegrationTest | Login, credenciales, permisos, expiración JWT, rotación/replay, logout y desactivación; incluye carrera de refresh. |
| BusinessIntegrationTest | CRUD HTTP, duplicados, Bean Validation, versión optimista, filtros, stock, transiciones, integración y SQL real. |

Los tests Mockito comprueban decisiones y ausencia de efectos prohibidos, no que un mock repita un valor configurado. Las pruebas de base usan PostgreSQL real, nunca H2. Las de negocio pasan por MockMvc con seguridad activada y después consultan las tablas para validar resultados persistidos.

## Preparación y aislamiento

`AbstractIntegrationTest` inicia un PostgreSQL por JVM y un servidor HTTP local. La infraestructura se publica con `@DynamicPropertySource`. Testcontainers/Ryuk retira el contenedor al finalizar. No hay `disabledWithoutDocker`: si falta Docker, la suite debe fallar visiblemente.

Cada escenario crea clientes y productos con identificadores únicos. Las transacciones HTTP terminan antes de verificar tablas; no se envuelve el test en `@Transactional`, lo que ocultaría problemas de commit/rollback. La suite se ejecuta secuencialmente salvo las carreras explícitas. El estado de transporte simulado se restaura tras cada fallo; no activar ejecución paralela global sin aislarlo.

## Casos decisivos

**Reserva parcial:** crear dos líneas ordenadas por producto; la primera tiene stock suficiente y la segunda no. Confirmar devuelve 409. Se verifica DRAFT, cero reservas, cero movimientos de ese pedido y saldos reservados intactos en ambas filas.

**Competencia:** dos peticiones HTTP, dos pedidos y cinco unidades físicas; cada pedido solicita cuatro. Una barrera libera ambos threads, uno obtiene 200 y el otro 409. Se comprueban cuatro reservadas, cinco físicas, una reserva, un movimiento y un pedido confirmado. Se repite tres veces con datos nuevos.

**Transporte:** una respuesta 503 deja el pedido confirmado, su reserva y sus físicas intactas. Un reintento exitoso consume una sola vez. Los tests de cliente también verifican timeout y respuesta inválida.

**Informes:** completar dos pedidos de un cliente y mantener otro borrador; fijar un intervalo histórico exclusivo para el escenario. El informe debe sumar solo completados y respetar el límite superior exclusivo. Low-stock incluye productos sin saldo y cuenta reservas reales.

## Antes de entregar un cambio

Reproducir primero el comportamiento, fijar una expectativa de negocio y ejecutar la prueba que lo detecta. Aplicar el cambio más pequeño, comprobar estado final y ejecutar `clean verify`. Si cambia un contrato, actualizar OpenAPI/colección/docs. Para cambios SQL, incluir evidencia del plan y probar con cardinalidad representativa. Las ejecuciones reales de la entrega se consignan en `docs/VERIFICATION.md`; no confundir esta guía con resultados ejecutados.

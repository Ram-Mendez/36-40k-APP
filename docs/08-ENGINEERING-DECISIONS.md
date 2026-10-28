# Decisiones y límites

## Tamaño y responsabilidad profesional

El objetivo es un backend empresarial de tamaño medio: operaciones coherentes, contratos, SQL, seguridad, pruebas, integración y capacidad de diagnóstico. El rango salarial mencionado es contexto del encargo, no una certificación ni una equivalencia universal entre sueldo y tecnologías. La competencia se demuestra modificando, investigando y defendiendo decisiones sobre este repositorio.

## Decisiones adoptadas

| Decisión | Motivo y contrapartida |
| --- | --- |
| Dos servicios con un núcleo modular | Núcleo transaccional para catálogo/pedidos/stock y cotizaciones en un proceso Spring Boot independiente; comunicación REST sin transacción distribuida. |
| Shipping sin base de datos | Cotización pura con configuración propia; no necesita persistencia ni acceso a tablas del núcleo. |
| Jenkins y Kind temporales | Práctica de CI/CD y Kubernetes en runners, sin servidor permanente. Usuario Jenkins fijo y autenticación obligatoria. |
| Java 21 / Spring Boot 3.5 | Stack solicitado, ecosistema estable y pruebas integradas. |
| JPA en maestros; JDBC en stock/informes | CRUD mantenible y SQL explícito donde los bloqueos/agregaciones importan. |
| PostgreSQL en tests | Las restricciones, bloqueos y SQL reales no se validan sustituyendo el motor por H2. |
| Bloqueo pesimista en stock/pedidos | Correctitud frente a competencia; reduce throughput por producto muy disputado, aceptable aquí. |
| Versiones optimistas en maestros | Detecta ediciones obsoletas sin mantener bloqueos durante interacción humana. |
| Auditoría en la transacción | Ningún evento de éxito sobrevive a rollback; intentos fallidos se investigan en logs. |
| Cotización externa fuera de transacción | Evita bloquear stock durante IO; revalida versión al confirmar cambios locales. |
| Refresh opaco y JWT con sid | Rotación y revocación inmediata; requiere lectura de sesión por petición. |
| Baja lógica | Conserva histórico y claves; los nuevos pedidos rechazan maestros inactivos. |
| BigDecimal / numeric | Importes exactos, límites y escala explícitos; moneda única EUR. |

## Alternativas descartadas

Catálogo, pedidos y stock permanecen juntos: separarlos exigiría diseñar consistencia distribuida para operaciones hoy atómicas. La cotización sí tiene un límite independiente y se ejecuta en otro servicio. Kubernetes se practica con Kind temporal. Kafka/RabbitMQ, CQRS, event sourcing, Terraform, Redis, gateway, Eureka y sagas no son necesarios para este alcance. No hay frontend ni sistemas de IA.

La auditoría es un historial operativo, no un sistema de cumplimiento inmutable contra un administrador de base de datos. Los movimientos de inventario explican reservas y expediciones; no sustituyen contabilidad financiera.

## Integración y despliegue real

Un consumidor podría ser un frontend, un ERP o un servicio de facturación que utilice esta API detrás de HTTPS y una pasarela. Si se necesitase publicar expediciones a mensajería, un outbox transaccional y consumidores idempotentes serían una evolución razonable; no se implementan sin necesidad actual. Contratar un envío real requeriría idempotency keys, reconciliación del proveedor y estados explícitos ante respuestas ambiguas. La consulta GET de cotización actual no genera ese efecto remoto.

El JAR/contenedor podría desplegarse en una máquina o plataforma de contenedores con PostgreSQL administrado, secretos, proxy TLS, copias, logs centralizados y CI/CD. El proyecto proporciona una base ejecutable y comprobable; no administra dicha infraestructura.

## Límites conocidos

- Un almacén, una moneda, sin impuestos, facturación, pagos, devoluciones ni compras.
- Roles fijos, sin ACL dinámica, MFA, SSO, recuperación de contraseña ni throttling integrado. La pasarela debe limitar intentos en una exposición real.
- No hay garantía de exactly-once para peticiones de creación repetidas por un cliente: las transiciones sí impiden repetir sus efectos. Idempotencia de creación es un ticket evolutivo.
- Paginación por offset y búsqueda con comodín; medir antes de abordar grandes volúmenes. No hay objetivos de millones de usuarios.
- Auditoría y sesiones requieren política futura de retención. No se borran automáticamente registros históricos.
- El entorno Compose usa datos y secretos locales explícitos; producción necesita aprovisionamiento y configuración propios.

Los resultados comprobados y cualquier limitación de herramientas de la entrega están en `VERIFICATION.md`; no se confunden con estas decisiones de producto.

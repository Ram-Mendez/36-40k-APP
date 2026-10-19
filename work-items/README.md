# Backlog de trabajo sobre esta aplicación

Estos tickets son trabajo posterior sobre el repositorio entregado. BOP-104/105/106 son ejercicios con una regresión **solo en una copia aislada** siguiendo `CONTROLLED-DEBUGGING.md`; no describen defectos abiertos de la versión principal. BOP-113 es una incidencia de dependencia reproducible por configuración. El resto son evoluciones o ampliaciones de verificación, no requisitos ya implementados presentados como faltantes.

| Ticket | Categoría | Resultado |
| --- | --- | --- |
| BOP-101 | Funcionalidad | Dirección de entrega acordada por pedido |
| BOP-102 | Funcionalidad | Descuento comercial con límites |
| BOP-103 | Funcionalidad | Idempotencia al registrar pedidos |
| BOP-104 | Error controlado | Precio histórico cambia al editar cantidad |
| BOP-105 | Error transaccional controlado | Cancelación deja una liberación parcial |
| BOP-106 | Error de seguridad controlado | Reutilizar refresh no revoca al sucesor |
| BOP-107 | SQL/rendimiento | Medir y optimizar informe comercial |
| BOP-108 | SQL/rendimiento | Búsqueda de catálogo con volumen representativo |
| BOP-109 | Autorizaciones | Restringir ajustes negativos a ADMIN |
| BOP-110 | Seguridad | Limitar intentos de autenticación |
| BOP-111 | Testing | Modelo de estados y carreras mixtas |
| BOP-112 | Testing | Regresión de ventanas temporales y zonas |
| BOP-113 | Integración/operación | Investigar transporte lento |
| BOP-114 | Integración | Evolución a expedición contratada idempotente |
| BOP-115 | Operación | Retención de sesiones y auditoría |

Para cerrar un ticket: reproducir o definir el caso, establecer criterios comprobables, implementar con alcance limitado, ejecutar tests pertinentes y `clean verify`, actualizar contrato/documentación, y entregar evidencia de resultados y límites. Las migraciones se añaden; no se reescriben versiones aplicadas.

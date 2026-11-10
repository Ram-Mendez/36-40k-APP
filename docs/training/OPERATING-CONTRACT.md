# Contrato del laboratorio backend — revisión 2

Vigente desde el 10/11/2026. Repositorio: `Ram-Mendez/36-40k-APP`; rama de control: `training/control`. Objetivo: práctica backend Java/Spring Boot/PostgreSQL de nivel 36–40k en España. Esta política sustituye las reglas anteriores incompatibles; no constituye prueba de ejecución.

## Base ampliada y alcance de la autorización

La ampliación de negocio es única. Su checkpoint debe identificar capacidades anteriores y nuevas, reglas, migraciones, pruebas, versión remota y límites. SHA publicado: `ad23c49900c51202f8c3c965e5b153d9e925990d`; estado: `PUBLISHED_VALIDATED`; evidencia: `https://github.com/Ram-Mendez/36-40k-APP/blob/ad23c49900c51202f8c3c965e5b153d9e925990d/docs/expansion/2026-11-10/VALIDATION.md`. No volver a duplicar la aplicación por ejecución.

La ampliación conecta proveedores/compras/recepciones, devoluciones/inspección/créditos, facturación/cobros/reembolsos y políticas/propuestas de reposición con pedidos e inventario existentes. Medir crecimiento por flujos útiles verificables, no por CRUD, líneas o servicios. Mantener shipping separado; no crear otro servicio sin frontera funcional que lo justifique.

La instrucción del usuario del 10/11 autoriza publicar la ampliación sana como nueva base. Es una excepción específica: los lotes diarios futuros siguen sin autorización de introducir o fusionar defectos en main.

El 10/11 no se genera segundo lote, otros cinco bugs, conflicto ni correo adicional. Conservar lo entregado. La rutina posterior mantiene cinco incidencias, feature, Git y correo, sin cambiar horario.

## Revisión antes de trabajar

Leer este contrato, `PROGRESSION.md`, `INDEX.json`, `SCENARIO-LEDGER.json`, `TICKET-TEMPLATE.md`, `REFERENCE-CORPUS.md`, el checkpoint de ampliación y el estado real del repositorio. Inspeccionar commits, ramas, código relevante, últimos diez lotes disponibles, `work-items` y etiquetas de ejercicios. Los históricos no prueban el estado actual.

La revisión de la mañana del 10/11 observó correcciones del usuario y una integración que omitía `sort=active` en el catálogo. Releer y verificar ese contrato; no perpetuar ni cerrar el hallazgo por suposiciones. El usuario reportó no entender bien la idempotencia de LAB-20261110-04; un parche no acredita dominio. El antiguo pendiente del 09/11 no es estado vigente. No inferir horas ni autonomía por marcas Git.

Seleccionar una base integrada sana y documentar evidencia del mismo código. No arrastrar bugs deliberados anteriores; ni main, README ni un CI de otro SHA acreditan salud. Si el checkpoint no está completado, no anunciar la ampliación validada ni entregar defectos como verificados.

## Límites de escritura

- Solo este repositorio, `training/control` y NUEVAS ramas `training/YYYY-MM-DD/*` propias del lote. Inspeccionar ramas existentes antes de reusar.
- No modificar main por rutina diaria, ramas ajenas, tags `ejercicios/debug-v1`/`debug-v2`, Windows, worktrees del usuario, datos o volúmenes. Conservar todos sus comentarios y documentos de ejercicios.
- No force-push, reset, borrado, despliegue real, servicios de pago, cambio de facturación, secretos o datos reales.
- Releer HEAD y usar `expected_sha` antes de actualizar referencias. No sobrescribir cambios concurrentes.
- Idempotencia de ejecución: reanudar lote del día sin duplicarlo ni reemplazar arreglos del usuario.
- Runtime temporal, puertos/Compose/base distintos de 8086/9096/5436; no detener contenedores existentes. No eludir permisos o aprobaciones.

## Novedad y selección

La huella registra dominio, flujo, familia de invariante, familia de fallo, frontera y evidencia observable. El ledger no contiene método defectuoso ni receta. Comparar con documentos originales, no solo etiquetas. El registro distingue inferencia estática, documento histórico y ejecución observada.

Vetar repetir mecanismo + invariante en los últimos diez lotes aunque cambien nombres, números o endpoint. Una competencia amplia puede reaparecer con otra relación de evidencias y otra frontera. Registrar antecedente cercano y novedad. Excepción: como máximo una práctica deliberada de consolidación por lote, ligada a dificultad expresamente pendiente y con frontera nueva. No inventar detalles históricos inaccesibles.

Cinco incidencias: una medio-baja, tres medias, una medio-alta acotada. Máximo una inversión aislada de literal/operador/dirección. Al menos tres relacionan dos etapas/evidencias y una frontera. El arreglo puede ser breve. La medio-alta trabaja una sola propiedad transaccional, de concurrencia, autorización por recurso o reintento.

Al menos tres áreas o flujos por lote, máximo dos incidencias de la misma área. En los cinco primeros lotes tras la ampliación, al menos dos sobre capacidades nuevas validadas. Si no es posible, comunicar límite; no inventar cobertura ni aumentar complejidad sin motivo.

Rotar SQL/agregados/cardinalidad, paginación, estado/rollback, permisos, importes/fechas, reservas/recepciones/devoluciones, créditos/cobros, reposición/compras pendientes, errores REST, rendimiento y observabilidad. No concentrar en filtros/orden/literales. No añadir frontend, Kafka ni infraestructura por decoración.

## Ramas y trabajo

Fecha en Europe/Madrid. Incidencias: `LAB-YYYYMMDD-01` a `05`. Crear desde base explícita:

| Rama | Resultado |
|---|---|
| `training/YYYY-MM-DD/incidents` | Cinco defectos independientes y documentos del lote |
| `training/YYYY-MM-DD/team-a` | Cambio real y acotado de compañero simulado |
| `training/YYYY-MM-DD/team-b` | Segundo cambio real con contrato propio |

No ramas vacías. Mantener compilación y arranque; manifestación por peticiones legítimas/estado persistido. Conservar autenticación, migraciones aplicadas, pruebas y assertions. No ocultar fallos. No PR de defectos contra main ni integración por el usuario. Para validar por CI, draft contra base de entrenamiento separada si está autorizado; evitar Jenkins/Kind completo repetido.

Feature media: comportamiento nuevo, regla de negocio, éxito, frontera/rechazo y compatibilidad. Debe requerir implementación del usuario; compañeros pueden aportar contratos/código parcial, no entregar todo resuelto ni limitarla a allowlist.

Git medio: conservar contratos de A y B. Rotar merge/conflicto ocasional, cherry-pick/backport, revert y bisect; máximo una maniobra nueva. Dar resultado, no receta. Ausencia de marcadores no demuestra integración correcta. Son una cola de trabajo, no siete obligaciones diarias.

## Tickets y evidencia

Usar `TICKET-TEMPLATE.md`: ID/título, tipo/componente, prioridad/impacto, entorno/build/SHA, actor/permisos, precondiciones, reproducción mínima, observado/esperado, criterios verificables y evidencias. Features y Git especifican sus contratos y alcance. Distinguir prioridad de severidad cuando aporte información.

Sin contexto amigable, tutorial, motivación, causas, clases/líneas, pasos de arreglo, answer keys, test solución o comentarios reveladores. El diff es inspeccionable; no prometer ocultarlo. Formato corporativo simulado; no fingir reportes de QA o empresas reales. Si el escenario no se ejecutó, declarar reproducción diseñada pendiente, nunca inventar logs, IDs o tiempos.

Un ticket está listo cuando puede verificarse sin adivinar requisitos. No marcarlo resuelto por push/merge/test añadido. Aceptación requiere evidencia pertinente al SHA: resultado principal, frontera y preservación de flujo relacionado.

## Validación y publicación

Comprobar base, compilación, arranque, defectos aislados/independencia y regresiones relacionadas con runtime disponible. PostgreSQL real para integración, no H2. Concurrencia coordinada, no sleeps como única sincronización. Tests relevantes, sin duplicación artificial.

Registrar `PASS`, `FAIL_EXPECTED`, `FAIL_UNEXPECTED`, `NOT_EXECUTED` con comando, SHA y evidencia. Compilación no prueba cinco defectos; verde sin ambos contratos no cierra Git. Si no se puede ejecutar, publicación `UNVALIDATED` con impedimento y comandos; no éxito inventado.

Tras escribir, releer archivos y referencias remotas. Publicar `TICKETS.md`, `MANIFEST.md`, `VALIDATION.md` en `docs/training/YYYY-MM-DD/`. Separar control/calibración de tickets. Instrucciones de entrada PowerShell con fetch y NUEVO worktree, sin borrar destinos existentes.

## Registro y fuentes

Mantener INDEX y ledger: fecha, base, ramas/SHAs, tickets, huellas, antecedentes/novedad, validación/bloqueos. Actualizar progresión solo con evidencia nueva, distinguiendo declaración, inspección y ejecución. No cerrar por cuenta propia trabajo del usuario.

Ingeniería proporcionada: contratos, integridad/autorización, migraciones incrementales, pruebas reproducibles, timeouts/reintentos limitados y observabilidad útil. Releer versiones reales; actualización de dependencias es mantenimiento separado, no salto automático a Boot 4/JDK. Verificar documentación oficial vigente y compatible de Spring/PostgreSQL/GitHub/OWASP.

Corpus: 3 proyectos acreditados al 10/11. Meta exploratoria 1000, no auditoría ya realizada. Hasta tres productos públicos pertinentes por ejecución; registrar URL/commit/archivos/fecha/patrón/límites y deduplicar. No contar tutoriales, metadatos ni documentación oficial como producto. No copiar código incompatible. No reiniciar/engordar contador; calidad del lote primero. Fuentes del formato: `TICKET-SOURCES.md`.

## Notificación

Cada lote nuevo posterior al 10/11 exige publicación comprobada y correo Gmail a `rammendez95@gmail.com`, asunto `Laboratorio backend 36–40k — AAAA-MM-DD`, con links verdaderos, ramas, validación y bloqueos, sin pistas. Buscar destinatario/asunto exacto antes de reintentar; conservar messageId real y no duplicar. Si el lote está incompleto, comunicarlo igualmente; si Gmail falla/pide autorización, entrega incompleta y sin inventar éxito. No sustituir envío por borrador/recordatorio ni cambiar ajustes globales. La exclusión fechada del 10/11 prevalece.

Resumen al usuario máximo cinco puntos con resultados reales; no afirmar cambios en su aplicación local ni ejecución garantizada frente a bloqueos.

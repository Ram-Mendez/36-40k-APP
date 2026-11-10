# Validación y evidencia — 2026-11-11

**Clasificación global:** `PUBLISHED_UNVALIDATED`; código remoto real con comprobaciones estructurales, **sin reproducción ejecutada**.

| Comprobación | Resultado | Referencia / evidencia |
|---|---|---|
| Base inicial comprobada | **PASS** (verificación de referencia) | `main=ad23c49900c51202f8c3c965e5b153d9e925990d`; ese SHA es el mismo de la [validación del 10/11](https://github.com/Ram-Mendez/36-40k-APP/blob/ad23c49900c51202f8c3c965e5b153d9e925990d/docs/expansion/2026-11-10/VALIDATION.md), que documenta 34 tests unitarios +100 de integración PostgreSQL +24 shipping y smoke 32 solicitudes/23 aserciones. Es evidencia histórica sobre **la base**, no sobre estos defectos. |
| Publicación de 5 alteraciones Java | **PASS** (control remoto) | Lectura de blob tras cada `update_file`, SHA del blob actualizado y fragmento esperado presentes; cinco archivos distintos de `incidents`. |
| Independencia estructural | **PASS** (solo archivos) | Los cinco cambios ocupan módulos/rutas de flujo diferentes; ninguna migración ni test alterado. No acredita independencia de efectos HTTP. |
| Ramas `team-a` y `team-b` | **PASS** (control remoto) | Cambios Java leídos tras push, refs `56dd8022` y `37d369b2`; ambas ramas divergen solo un commit desde la base. |
| Comparación commits vs base | **PASS** (control remoto) | GitHub `compare_commits` devolvió `ahead`, sin retraso frente a base, para las tres ramas tras publicar tickets. |
| `mvnw.cmd -B -ntp -DskipTests package` | **NOT_EXECUTED** | No hay checkout completo ni Maven Wrapper/dependencias accesibles al ejecutor; GitHub API no compila. |
| `mvnw.cmd -B -ntp verify` (PostgreSQL) | **NOT_EXECUTED** | Contenedor disponible sin checkout de red, Docker/PostgreSQL ni Maven; no usar H2 como sustituto. |
| Shipping `mvnw.cmd -f services/shipping-service/pom.xml verify` | **NOT_EXECUTED** | Servicio original intacto; suite solo acreditada sobre la base histórica. |
| Arranque/readiness con puertos temporales | **NOT_EXECUTED** | No hay runtime arrancado del lote. |
| LAB-20261111-01 / borde temporal | **NOT_EXECUTED** | Consultar compra de fecha límite y contraste `totalElements` con fecha UTC. |
| LAB-20261111-02 / recepción y propuesta | **NOT_EXECUTED** | Reproducir recepción parcial y verificar compra, inventario y propuesta. |
| LAB-20261111-03 / inspección | **NOT_EXECUTED** | Comparar disposiciones recuperables/deterioradas, stock y movimientos. |
| LAB-20261111-04 / reversión | **NOT_EXECUTED** | Asiento histórico, factura y cuentas a cobrar. |
| LAB-20261111-05 / reintento | **NOT_EXECUTED** | Comprobante original, nuevo envío de referencia y persistencia final. |
| Comprobación de no regresión fuera de los cinco casos | **NOT_EXECUTED** | Precisa tests HTTP/SQL sobre base y rama modificada. |
| Validación integración Git A+B | **NOT_EXECUTED** | Es tarea del usuario; ninguna rama se ha fusionado automáticamente. |

**Lectura correcta:** las modificaciones deliberadas se han publicado y se han verificado estáticamente por GitHub; ningún status `FAIL_EXPECTED` se concede hasta observar realmente un test o respuesta fallida en el SHA correspondiente. La compilación no se puede inferir del éxito del push ni de la ausencia de cambios en firmas públicas.

### Comandos de validación pendientes

En un **nuevo worktree** con JDK 21 y Docker/Testcontainers dedicado:

```powershell
.\mvnw.cmd -B -ntp -DskipTests package
.\mvnw.cmd -B -ntp verify
.\mvnw.cmd -B -ntp -f services/shipping-service/pom.xml verify
```

Usar datos únicos en una base de pruebas limpia; ejecutar una sola vez cada escritura salvo cuando el ticket exige un reintento. Registrar SHA de branch, comando, respuesta HTTP, `requestId` solo si existe de verdad, consulta persistida, caso principal, frontera y comportamiento relacionado. No modificar assertions para transformar errores esperados en verdes.

El escenario global puede requerir corregir varios fallos a la vez; para validar independencia, probar cada alteración desde el SHA de base en worktrees temporales aislados y no interferir con la base de datos de usuario. La coordinación de concurrencia, si se añadiera, debe ser determinista, no basada solo en `sleep`.

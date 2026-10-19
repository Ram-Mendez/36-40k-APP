# Autenticación, sesiones y permisos

No hay alta pública. ADMIN crea y administra usuarios en `/api/v1/users`. Las contraseñas se codifican con BCrypt, coste 12; nunca se devuelven hashes. La creación valida longitud y el límite de 72 bytes UTF-8 propio de BCrypt.

## Ciclo de sesión

Login comprueba credenciales y usuario activo dentro de una transacción. Se crea `auth_session` y se emite JWT HS256 con issuer `business-operations`, subject, uid, sid, jti, iat y exp. Por defecto el access dura 15 minutos. `JWT_SECRET` debe tener al menos 32 bytes UTF-8; una clave débil impide arrancar.

El refresh es un valor aleatorio de 48 bytes codificado en Base64 URL, válido siete días por defecto. PostgreSQL almacena solo su hash SHA-256. `POST /auth/refresh` recibe `{"refreshToken":"..."}`. La operación bloquea el usuario y la sesión, marca el anterior como utilizado/revocado y entrega otro par. El vencimiento absoluto de la familia no se prolonga en cada renovación.

**Renovar también invalida el access anterior** porque está vinculado al sid revocado. El cliente debe reemplazar ambos tokens de forma atómica. Si se reutiliza un refresh ya usado, se revoca toda su familia y se devuelve 401. La revocación se confirma antes de emitir el error: lanzar una excepción dentro de esa transacción desharía una defensa importante.

`POST /auth/logout` requiere el access vigente y revoca su familia; después sus access y refresh quedan inutilizables. Desactivar al usuario revoca todas sus sesiones. Cada petición verifica la sesión y lee el rol actual; se obtiene revocación inmediata a cambio de consultas a PostgreSQL.

`PUT /api/v1/auth/password` recibe `currentPassword` y `newPassword` con un access vigente. Verifica la contraseña actual y cambia el hash dentro de la misma transacción que revoca **todas** las sesiones del usuario, incluida la actual. Responde 204; después se debe iniciar sesión con la nueva contraseña. Una contraseña actual incorrecta devuelve 401 sin modificar hash ni sesiones. No existe recuperación anónima por correo.

## Matriz de permisos

| Operación | ADMIN | OPERATIONS | READONLY |
| --- | --- | --- | --- |
| Consultar clientes, productos, pedidos, inventario e informes | Sí | Sí | Sí |
| Crear/editar/desactivar clientes y productos | Sí | Sí | No |
| Crear/modificar/confirmar/cancelar/completar pedidos | Sí | Sí | No |
| Ajustar inventario | Sí | Sí | No |
| Administrar usuarios | Sí | No | No |
| Consultar auditoría | Sí | No | No |

Las operaciones usan autorización de método mediante `@PreAuthorize`; la cadena HTTP exige autenticación por defecto. Cambios administrativos se serializan y no permiten dejar el sistema sin ADMIN activo ni que un administrador se desactive o rebaje a sí mismo.

## Respuestas y configuración

Sin token, con firma incorrecta, issuer inválido, sesión revocada o token vencido: 401 y `WWW-Authenticate: Bearer`. Con autenticación válida y rol insuficiente: 403. Las respuestas no contienen contraseñas, refresh tokens ni trazas de excepciones. El log de petición no registra bodies ni cabecera Authorization.

CSRF está desactivado porque se usa Authorization explícito, no cookies de sesión. No se habilita CORS permisivo: un frontend futuro requiere una lista explícita de orígenes. Publicar detrás de HTTPS y una pasarela con limitación de intentos; el repositorio no implementa MFA, recuperación por correo, SSO ni throttling distribuido.

Variables: JWT_SECRET, JWT_ACCESS_TTL y JWT_REFRESH_TTL. Swagger es público solo cuando `security.swagger-enabled=true` (dev/test); en otro perfil requiere autenticación. Health expone estado sin detalles. No activar dev en producción: contiene datos y secretos de demostración.

## Credenciales exclusivamente de desarrollo

`admin`, `operations` y `readonly`, todos con contraseña `DevPassword123!`. Los datos se cargan exclusivamente desde la ubicación Flyway de desarrollo/test. El Compose incluido es un entorno local con puertos vinculados a loopback.

Para una base nueva sin perfil dev, establecer `BOOTSTRAP_ADMIN_ENABLED=true`, `BOOTSTRAP_ADMIN_USERNAME` y `BOOTSTRAP_ADMIN_PASSWORD` junto a secretos propios. `BootstrapAdminInitializer` crea un ADMIN únicamente si no existe ningún usuario, bajo un bloqueo transaccional y con auditoría. Rechaza la contraseña conocida de desarrollo y valida mínimo 12 caracteres/máximo 72 bytes UTF-8. Tras el primer arranque correcto, desactivar bootstrap y retirar su contraseña del entorno. Nunca modifica una cuenta existente.

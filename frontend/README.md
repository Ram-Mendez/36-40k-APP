# Nexo · Operations workspace

Frontend Angular de la aplicación existente. Incluye vista general, pedidos, inventario,
catálogo, clientes, informes, actividad, equipo y cuatro recorridos de negocio.

## Desarrollo

Desde esta carpeta:

```powershell
npm ci
npm start
```

Abre `http://localhost:4200`. El proxy dirige `/api` a `http://localhost:8086`.
El backend debe estar arrancado para iniciar sesión y realizar operaciones reales.
La caché persistente de Angular está desactivada para evitar el cierre nativo observado
con Node 25 en este equipo; se recomienda usar una versión LTS compatible con Angular 21.

## Demo y datos reales

- Sin sesión se abre una demo identificada con datos ilustrativos, sin peticiones de escritura.
- Búsqueda, filtros, periodos del gráfico, exportaciones y detalles funcionan en la demo.
- Los botones de escritura piden conectar una cuenta real; no simulan que la operación se guardó.
- Al iniciar sesión se vacían los ejemplos y se cargan datos de la API. Los fallos de carga
  se muestran explícitamente; no se sustituyen por datos de ejemplo.
- El servidor aplica los permisos. Auditoría y gestión de usuarios requieren ADMIN.
- Las rutas se conservan mediante fragmentos (`#/orders`); recargar no requiere configurar
  redirecciones nuevas en Spring Boot.

## Compilar para Spring Boot

```powershell
npm run build:backend
```

Genera `dist/frontend/browser` y copia el resultado a `../src/main/resources/static`.
El script conserva recursos ajenos a Angular; los bundles antiguos con hash pueden permanecer,
pero el nuevo `index.html` referencia únicamente el build actual. Reinicia o recompila la
aplicación Java para actualizar sus recursos y abre `http://localhost:8086`.

## Organización

- `src/app/app.ts` / `app.html`: navegación y vistas de negocio.
- `src/app/workspace.service.ts`: estado y lectura de las colecciones paginadas.
- `src/app/api.service.ts`: autenticación, peticiones y errores.
- `src/app/actions.ts`: registro de operaciones existentes, también accesible en Ajustes.
- `src/app/demo-data.ts`: ejemplos separados de la API.
- `src/app/operations.models.ts`: tipos de los contratos del backend.
- `src/app/icon.ts`: iconografía SVG compartida.
- `src/styles.scss` y `src/styles/`: colores, componentes y estilos adaptables.
- `public/assets/fonts`: Manrope local y su licencia OFL; el build no descarga fuentes.

El recorrido de un pedido conserva las reglas del backend: crear borrador, añadir líneas,
confirmar para reservar y completar para registrar la salida. La demo no modifica esas reglas.

## Comprobaciones

`npm run build` verifica TypeScript y las plantillas Angular en modo estricto. Para validar
la interfaz, revisar navegación, búsqueda, paginación, filtros de inventario, detalle del
pedido, entrada y salida de sesión, exportaciones y el menú móvil. Las operaciones reales
dependen del backend y de los permisos de la cuenta conectada.

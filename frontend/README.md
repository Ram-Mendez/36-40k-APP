# Nexo operations workspace

The Angular workspace includes orders, inventory, products, customers, reports, activity, team settings, a guided warehouse lab and an animated warehouse.

## Almacén en vivo

Abre `/#/warehouse` para ver el plano con ocho zonas, operarios, estanterías, recepción, preparación, salida, devoluciones, oficina y control. Puedes seleccionar pedidos y productos, abrir sus operaciones y consultar el catálogo de endpoints.

- Las operaciones enviadas desde los formularios y el laboratorio aparecen en el historial de la sesión. Las consultas automáticas de sincronización no generan recorridos.
- El personaje espera la respuesta del servidor. Solo una respuesta satisfactoria permite representar el resultado. Reservar marca unidades; completar un pedido representa la salida; recibir mercancía representa la entrada. Un error conserva el motivo y no muestra un movimiento completado.
- Las cantidades del inventario se recargan desde el backend. La animación nunca suma ni resta stock. El plano y las etapas del recorrido son explicativos: el backend no registra posiciones físicas ni seguimiento del transportista.
- Los ejemplos se identifican como simulación y no envían peticiones. Reproducir un evento solo repite su animación. El historial es local a esta sesión, no sustituye a la auditoría del servidor y se limpia al cerrar sesión.
- Los formularios de recepción y devolución permiten editar varias líneas en JSON. Conserva el `requestId` y los mismos datos al reintentar la misma operación; una operación nueva necesita otro UUID. La política de reposición omite `version` al crear y necesita la versión actual al actualizar.

Después de ejecutar una operación, pulsa **Ver recorrido en almacén** para cerrar el formulario y reproducir su resultado. El laboratorio tiene el mismo acceso. Las operaciones de otros navegadores aparecen en los datos al actualizar; sus animaciones no se retransmiten a esta sesión.

### Comprobaciones

```powershell
npm test
node scripts/check-endpoint-coverage.mjs
npm run build:backend
```

Las pruebas del almacén verifican reservas frente a salidas, devoluciones con unidades dañadas, errores, consultas, concurrencia, reintentos idempotentes y eliminación de datos sensibles del historial.

## Start the frontend

From this directory, run:

```powershell
npm ci
npm start
```

Open [Nexo](http://localhost:4200). The development proxy forwards API requests to the backend on port 8086. Use an Angular 21-compatible LTS release of Node.js. Persistent Angular caching is disabled for this project.

## Run the November 12 warehouse lab

Start the lab backend on port 38086. Run `npm run lab` in this directory and open the [Angular lab](http://localhost:4200/#/lab), or open the [lab served by Spring Boot](http://localhost:38086/#/lab) after building the frontend. Choose an exercise and sign in. Each step shows its request and response and saves the IDs needed in later steps. The draft purchase exercise checks the server's OpenAPI document for endpoint availability.

## Demo and live data

The demo uses illustrative data, with working search, filters, charts, order details and CSV exports. Sign in to read and write live records. API failures appear in the workspace. The server enforces permissions. Audit and user management require an ADMIN account.

## Build for Spring Boot

Run `npm run build:backend` to compile TypeScript and templates and copy the browser build into `../src/main/resources/static`. Restart or rebuild the backend to serve the new bundle. Hash routes such as `#/orders` support direct navigation and reloads.

## Project layout

- `src/app/app.ts` and `app.html` implement the workspace views.
- `src/app/workspace.service.ts` loads and stores paginated API data.
- `src/app/api.service.ts` handles authentication, API requests and errors.
- `src/app/actions.ts` defines the operations available in Settings.
- `src/app/operations.models.ts` defines API types. `demo-data.ts` contains the demo records.
- `src/app/warehouse-exercises.ts` defines the lab steps. `warehouse-lab.ts` runs them.
- `src/app/warehouse-scene.*` implements the interactive warehouse. `warehouse-activity.ts` describes business effects; its service and HTTP interceptor record explicit operations. `warehouse-actions.ts` completes the endpoint forms.
- `src/styles/` defines shared styles. `public/assets/fonts/` contains the locally hosted Manrope font and its OFL license.

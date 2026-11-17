# Consolidación del 17/11/2026

Se conserva una sola carpeta de trabajo: `C:\Users\balar\Desktop\36-40k-APP`.

- Progreso del 16 guardado en `045554f`, publicado en `origin/codex/training-2026-11-16`.
- Progreso del 17 guardado en `c69a894`, publicado en `origin/codex/training-2026-11-17`.
- Historial de ambas ramas conservado al consolidar. La práctica V3 ya estaba en `origin/codex/incidencias-autonomas-v3` y es antecesora de main.
- Se retiró el incremento manual de `Product.version`: JPA gestiona la versión. Se corrigió el ejercicio pendiente de respuestas tardías de Angular y se retiró la caché ya sin uso de cotizaciones.
- Las tres copias auxiliares se archivaron antes de retirar sus worktrees. Respaldo local: `C:\Users\balar\.codex\archives\36-40k-consolidation-2026-11-17`. Incluye código, notas, configuración local, logs y un bundle de todas las referencias Git. Excluye dependencias y salidas regenerables (`node_modules`, `target`, `dist`, `.angular`).
- Las bases de laboratorio y sus volúmenes se conservaron. `compose.lab.yaml` reutiliza la base existente del 17 en 35438. No se borraron datos de PostgreSQL.

## Validación de la base reparada

Maven `verify` del núcleo pasó con 52 pruebas unitarias y 105 de integración; Shipping pasó sus 26 pruebas. Pasaron las 29 pruebas del almacén Angular, la comprobación de 70/70 formularios, los dos checks asíncronos del 17 y la compilación/copia del frontend al backend.

Los nuevos contratos de aceptación también se ejecutaron ANTES de introducir los defectos: cuatro comprobaciones Java y dos de Angular, todas verdes. Los tres checks de integración que modifican datos usan PostgreSQL aislado de Testcontainers, sin tocar las bases del usuario.

Las configuraciones estables son `Backend APP` y `Shipping APP`, guardadas en `.run`. Se mantiene un único frontend y un único directorio. La base correcta queda publicada en `main`; los nuevos defectos solo pertenecen a la rama de práctica indicada en EMPIEZA-AQUI.md.

También se verificó el arranque real con los argumentos de Backend APP y Shipping APP. Los cuatro preparadores de datos (01, 03, 04 y 05) se ejecutaron con Windows PowerShell y peticiones HTTP. El reintento con líneas reordenadas devolvió la misma recepción en la base correcta. Estos datos de prueba quedan en la base de laboratorio, incluidos 101 productos del ejercicio 05.

# Manifiesto · laboratorio 2026-11-10

**Repositorio:** `Ram-Mendez/36-40k-APP`.

**Base integrada de partida:** `a6bc8058415a21c3dc75564974e375e69bdab9a9` (main observado el 2026-11-10, no implica suite sana).

**Código de incidencias publicado:** `bcfd7035f0b82473649a3e11e33802122819066a`, rama `training/2026-11-10/incidents`. Este documento pertenece a un commit posterior; consulta el HEAD remoto para obtener el SHA de documentación más reciente.

**Rama compañera A:** `training/2026-11-10/team-a` — `4a3f376b0aada3803471161301176f83b6b75070`.

**Rama compañera B:** `training/2026-11-10/team-b` — `2cd1fbd9354d0193ff09b8007496a56ddef2c89a`.

**Incidencias del lote:** LAB-20261110-01, LAB-20261110-02, LAB-20261110-03, LAB-20261110-04, LAB-20261110-05; ninguna marcada como resuelta. **Cambios adicionales:** CAT-20261110-A y GIT-20261110-01.

**Difusión:** NO desplegar esta rama en servicios accesibles a terceros; contiene fallos intencionados, incluido un escenario de sesión. Uso exclusivo de entrenamiento, sin reutilizar credenciales de producción. Mantener el trabajo previo sin modificar.

## Preparación segura en PowerShell (desde tu clon existente)

```powershell
git status --short
git fetch origin
git worktree add --detach ..\lab-2026-11-10-incidents origin/training/2026-11-10/incidents
git worktree add --detach ..\lab-2026-11-10-integration origin/training/2026-11-10/team-a
```

Los destinos deben ser **nuevos y vacíos**; si un comando informa de que ya existen, detente y utiliza otra ruta, sin borrarlos. Para integrar el segundo trabajo se usa `origin/training/2026-11-10/team-b` en el worktree independiente, creando una rama local propia antes de realizar modificaciones. No hagas merge ni pull de estos ejercicios en `main`.

**Infraestructura:** la máquina del usuario no ha sido tocada. Cualquier Compose o PostgreSQL del laboratorio debe emplear nombre de proyecto, base de datos y puertos distintos de los de uso habitual (8086, 9096, 5436); no detener contenedores existentes. **Compilación y ejecución:** ver `VALIDATION.md`.

**Importante:** los tests previos y migraciones no se han modificado; esta rama puede tener tests que fallen **por diseño**, lo cual no equivale a compilación verificada.

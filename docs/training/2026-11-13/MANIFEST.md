# Manifiesto del lote · 13/11/2026

**Estado:** `PUBLISHED_UNVALIDATED` hasta que se ejecuten los casos de prueba. **Fecha civil de ejercicio:** 13/11/2026 (Europe/Madrid). **Base sana:** `ad23c49900c51202f8c3c965e5b153d9e925990d`, checkpoint validado el 10/11. No acumula defectos intencionados de otros días.

| Rama remota | Commit con código nuevo | Cambios Java |
|---|---|---|
| `training/2026-11-13/incidents` | `5b354475c8ce3b777c6272404f812a71395fca80` | 5 |
| `training/2026-11-13/team-a` | `8e2e918fcb5bce22523fcc2d327432d45c4d44d8` | 1 |
| `training/2026-11-13/team-b` | `0308a53693c2ccaecd9238eca773726058fb2fa6` | 1 |

**Incidencias:** LAB-20261113-01..05. **Historia:** FEATURE-20261113-01. **Integración:** GIT-20261113-01. Ver [TICKETS.md](TICKETS.md) y [VALIDATION.md](VALIDATION.md).

**Historial:** no se han tocado `main`, ejercicios previos, tags, migraciones, tests, el servicio shipping ni contenedores del usuario. Los commits de documentación se añaden a incidents después del commit de código. La feature queda para el desarrollador.

## Inicio seguro en PowerShell

```powershell
git status --short
git fetch origin
git branch -r --list "origin/training/2026-11-13/*"
git worktree add ..\lab-2026-11-13 -b practice/2026-11-13 origin/training/2026-11-13/incidents
```

No ejecutar el último comando si el directorio/rama destino ya existe; elegir uno nuevo. Trabajar en branch personal, no modificar ramas de entrenamiento. Para pruebas usar JDK 21, PostgreSQL 16 aislado y puertos diferentes de `8086`, `9096`, `5436`.

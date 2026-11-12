# Manifest del lote — 12/11/2026
**Estado:** `PUBLISHED_UNVALIDATED` (publicación verificada; pruebas y reproducciones pendientes).
**Base:** `ad23c49900c51202f8c3c965e5b153d9e925990d`, mismo SHA de la [ampliación validada del 10/11](https://github.com/Ram-Mendez/36-40k-APP/blob/ad23c49900c51202f8c3c965e5b153d9e925990d/docs/expansion/2026-11-10/VALIDATION.md). No utilizar la validación histórica como evidencia de los defectos.

| Rama | SHA de código | Archivos Java modificados |
|---|---|---|
| `training/2026-11-12/incidents` | `5c40fc0c98b36dfd52c02b7f73fd0e80fdbd5a69` | 5 |
| `training/2026-11-12/team-a` | `e638c890b7a3bdda7dfa4b149b58e01ccc946871` | 1 |
| `training/2026-11-12/team-b` | `b3371462edddaa1841337c53cc7d1dae908702e0` | 1 |

**Tickets:** `LAB-20261112-01..05`, `FEATURE-20261112-01`, `GIT-20261112-01` en [TICKETS.md](TICKETS.md). Los compañeros no resuelven la feature del usuario. Nada se ha incorporado a `main`; no se han alterado los tests, migraciones, shipping ni tags previos.

## Entrada PowerShell (solo directorio y rama personal NUEVOS)
```powershell
git status --short
git fetch origin
git branch -r --list "origin/training/2026-11-12/*"
git worktree add ..\lab-2026-11-12 -b practice/2026-11-12 origin/training/2026-11-12/incidents
```
Si el destino existe, elegir otro nombre; no borrar ni sobrescribir. Integrar compañeros en **otra rama personal** desde la base. Para runtime, usar PostgreSQL, proyecto Compose y puertos separados de 8086/9096/5436; no detener contenedores existentes.

**Comprobado:** comparaciones GitHub frente a la misma base (`ahead=1` para cada rama), 5+1+1 archivos Java. **Pendiente:** compilación y reproducción de las cinco incidencias. El HEAD documental de incidents es un commit posterior al SHA de código y debe consultarse directamente en remoto. [Validación](VALIDATION.md).

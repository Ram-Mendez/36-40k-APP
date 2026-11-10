# Plantillas de work items

Adaptación al laboratorio de las guías oficiales citadas en `TICKET-SOURCES.md`. No acredita una incidencia corporativa real. Los campos se completan con hechos disponibles; usar «pendiente» donde no existe evidencia. La dificultad/calibración se mantiene fuera de los tickets.

## Incidencia

```markdown
## LAB-YYYYMMDD-NN · [Componente] Síntoma observable

| Campo | Valor |
|---|---|
| Tipo | Bug |
| Prioridad | P1/P2, justificada por impacto |
| Componente | Dominio o flujo |
| Entorno / build | Rama, SHA y configuración relevante |
| Evidencia | Reproducido / reproducción diseñada pendiente |

### Impacto
Consecuencia concreta para la operación.

### Condiciones
Actor, permisos, estado inicial y datos mínimos.

### Reproducción
1. Operación observable.
2. Operación observable.
3. Consulta para verificar resultado y estado persistido cuando proceda.

### Resultado observado
Respuesta o estado capturado; si no se ejecutó, indicarlo expresamente.

### Resultado esperado
Contrato de negocio inequívoco.

### Criterios de aceptación
- AC1: comportamiento principal verificable.
- AC2: rechazo o frontera pertinente.
- AC3: preservación de un comportamiento relacionado.

### Evidencia disponible
Enlaces o capturas reales; pendientes expresos.
```

## Petición de negocio

```markdown
## FEATURE-YYYYMMDD-01 · Resultado de negocio

Tipo: Story. Componente/epic: … Prioridad: … Build/base: …

### Necesidad
Actor, necesidad y resultado útil, sin introducción pedagógica.

### Alcance
Comportamiento observable a incorporar y exclusiones necesarias para acotarlo.

### Reglas
Regla principal, precondiciones y contrato con funciones existentes.

### Aceptación
- AC1: caso principal con resultado verificable.
- AC2: rechazo o frontera y ausencia de efectos parciales.
- AC3: compatibilidad con comportamiento previo pertinente.

### Dependencias y evidencia
Contratos de ramas de compañeros y evidencia disponible; implementación propia pendiente.
```

## Integración Git

```markdown
## GIT-YYYYMMDD-01 · Resultado de integración

Tipo: Task. Base: … Ramas/SHAs: …

### Entrega recibida
Contrato observable A y contrato observable B.

### Resultado requerido
Integración en rama de trabajo nueva preservando ambos contratos y comportamiento previo.

### Aceptación
- AC1: contrato A conservado y comprobado.
- AC2: contrato B conservado y comprobado.
- AC3: compatibilidad y evidencia del SHA resultante.

### Evidencia de entrega
Referencias reales y pruebas pendientes/ejecutadas; no comandos solución.
```

## Revisión de calidad previa a publicar

- Título explica el resultado/síntoma sin revelar la causa.
- Datos permiten reproducir sin adivinar reglas; límites son explícitos.
- Cada criterio expresa resultado verificable y no prescribe implementación.
- Ninguna ejecución, persona, equipo, log o ID ficticio aparece como real.
- La feature deja trabajo propio y la tarea Git exige ambos contratos.

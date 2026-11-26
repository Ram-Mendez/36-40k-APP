# Diez incidencias de Spring Boot · Nivel 5–6/10

Arranca **Backend APP** en Debug desde `36-40k-APP`. Flyway prepara los datos en la base habitual `lab_20261117` cuando arranca Spring Boot.

En [Swagger](http://localhost:38088/swagger-ui/index.html), inicia sesión como `operations` y abre el grupo **Traslados de almacén**. Las rutas de los diez tickets empiezan por `/api/v1/warehouse-transfers`.

| Ticket | Dificultad estimada | Incidencia |
| --- | --- | --- |
| [INC-36](INC-36-hecho-25-11.md) | 5/10 | El camión admite una carga que supera uno de sus límites |
| [INC-35](INC-35-hecho-25-11.md) | 6/10 | El total usa el tamaño de caja del primer producto |
| [INC-38](INC-38-hecho-25-11.md) | 6/10 | El reparto recomienda cargar más bultos de los que hay |
| [INC-37](INC-37-hecho-25-11.md) | 6/10 | La fecha prevista cuenta el sábado como día laborable |
| [INC-31](INC-31-hecho-25-11.md) | 6/10 | La báscula rechaza el peso cuando cambia el número de decimales |
| [INC-29](INC-29-hecho.md) | 6/10 | PATCH no borra la referencia del traslado |
| [INC-34](INC-34-hecho-25-11.md) | 6/10 | Un bulto refrigerado y dañado pasa directo a cámara |
| [INC-32](INC-32-hecho-25-11.md) | 6/10 | El inventario confunde un lote sin contar con un lote vacío |
| [INC-28](INC-28-hecho.md) | 6/10 | El muelle acepta un turno dentro de otro |
| [INC-27](INC-27-hecho.md) | 6/10 | Dos partes sin motivo crean dos saldos para el mismo producto |

## Ejercicios avanzados opcionales

[INC-25 · Transacciones](INC-25.md), [INC-26 · Eventos](INC-26.md), [INC-30 · Bloqueos](INC-30-hecho-25-11.md) e [INC-33 · Caché HTTP](INC-33-hecho-25-11.md).

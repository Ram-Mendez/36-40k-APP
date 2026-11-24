# Diez incidencias avanzadas de Spring Boot · INC-25 a INC-34

Arranca **Backend APP** en Debug desde `36-40k-APP`. Flyway prepara los datos en la base habitual `lab_20261117` cuando arranca Spring Boot.

En [Swagger](http://localhost:38088/swagger-ui/index.html), inicia sesión como `operations` y abre el grupo **Traslados de almacén**. Las rutas de los diez tickets empiezan por `/api/v1/warehouse-transfers`.

| Ticket | Dificultad estimada | Incidencia |
| --- | --- | --- |
| [INC-25](INC-25.md) | 8/10 | El rechazo de una inspección desaparece del historial |
| [INC-26](INC-26.md) | 8/10 | El traslado sale y el consumidor nunca recibe el aviso |
| [INC-27](INC-27.md) | 8/10 | Dos partes sin motivo crean dos saldos para el mismo producto |
| [INC-28](INC-28.md) | 7/10 | El muelle acepta un turno dentro de otro |
| [INC-29](INC-29.md) | 7/10 | PATCH no borra la referencia del traslado |
| [INC-30](INC-30.md) | 8/10 | Una fila bloqueada detiene toda la cola de preparación |
| [INC-31](INC-31.md) | 7/10 | La báscula rechaza el peso cuando cambia el número de decimales |
| [INC-32](INC-32.md) | 7/10 | El inventario confunde un lote sin contar con un lote vacío |
| [INC-33](INC-33.md) | 8/10 | El cliente conserva datos antiguos después de editar el traslado |
| [INC-34](INC-34.md) | 7/10 | Un bulto refrigerado y dañado pasa directo a cámara |

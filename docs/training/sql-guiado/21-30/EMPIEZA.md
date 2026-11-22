# SQL de almacén · Casos 21–30

Abre la consola de `lab_20261117@localhost` en IntelliJ, puerto `35438`. Los diez casos usan las mismas tablas de `sql_lab20` y son consultas de lectura.

Ejecuta esta consulta para comprobar la conexión. Debe devolver `lab_20261117 | 6`.

```sql
SELECT current_database() AS base, COUNT(*) AS expediciones
FROM sql_lab20.expediciones;
```

Si has recreado la base y falta el esquema, ejecuta [preparar.sql](../11-20/preparar.sql) antes de abrir el primer caso.

| Caso | Qué falla |
| --- | --- |
| [21](21.md) | El informe pide reponer más de lo que falta |
| [22](22.md) | Dos ventas desaparecen del total |
| [23](23.md) | El pasillo vacío se borra del mapa |
| [24](24.md) | La alerta de stock se fija en cada movimiento |
| [25](25.md) | El mismo lote se cobra dos veces en el informe |
| [26](26.md) | Una salida de CAJA hace desaparecer a CINTA |
| [27](27.md) | El mapa imprime el pasillo como almacén |
| [28](28.md) | El porcentaje de unidades cambia según cómo se repartan los envíos |
| [29](29.md) | Dos operarios hacen doce bultos y solo se anotan doce |
| [30](30.md) | Una tarifa de CAJA borra el cargo de CINTA |

Empieza por el 21. Cada guía muestra lo que pide el informe, lo que devuelve el SQL con el fallo y una consulta corta para comprobar los datos. Copia el SQL a la consola para probar tus cambios.

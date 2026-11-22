# SQL de almacén · Casos 15–20

En IntelliJ abre **Database → lab_20261117@localhost → New → Query Console** (puerto **35438**). Todos los casos usan el esquema `sql_lab20`. Son consultas de lectura y puedes repetirlas en cualquier orden con una sola consola.

Para comprobar la conexión, selecciona esta consulta y pulsa **Ctrl+Enter**. Debes ver **lab_20261117 | 6**.

```sql
SELECT current_database() AS base, count(*) AS expediciones
FROM sql_lab20.expediciones;
```

Si recreas la base, ejecuta [preparar.sql](preparar.sql) entero en esa conexión. Con los datos ya cargados, abre el caso 15.

| Caso | Qué falla |
| --- | --- |
| [15](15.md) | El stock sale en dos filas por producto |
| [16](16.md) | El informe confunde lotes con unidades |
| [17](17.md) | Faltan envíos sin descuento |
| [18](18.md) | La lista pone primero a quien preparó menos |
| [19](19.md) | El cierre pierde los envíos del último día |
| [20](20.md) | El informe muestra el estante como si fuera su pasillo |

Cada guía muestra el resultado esperado, el fallo y qué parte de la consulta revisar. Haz tus cambios en la consola para poder volver al SQL original.

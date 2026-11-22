# Diez bugs de SQL en el lab 17

**Tanda actual · [Ejercicios 51–60](51-60/EMPIEZA.md)**. Usan datos de práctica y consultas basadas en métodos Java del proyecto.

[Casos 21–30](21-30/EMPIEZA.md) en la misma conexión de IntelliJ.

**Continúa con los [casos 15–20](11-20/EMPIEZA.md)** en la misma conexión de IntelliJ.

**Empieza por Eva.** Hay cuatro clientes.
Ventas pide una fila por cliente,
incluso si todavía no ha comprado.
En el informe solo aparecen tres.
Tu primer trabajo consiste en descubrir en qué paso desaparece Eva.
## Abre tu consola
1. En IntelliJ abre **Database** y elige **lab_20261117@localhost** (puerto `35438`). Abre su consola SQL. PostgreSQL es lo único que tiene que estar arrancado.
2. Pega estas dos líneas juntas, selecciónalas y pulsa **Ctrl+Enter**. Si IntelliJ pregunta qué ejecutar, elige la selección.

   ```sql
   SELECT current_database() AS base,
          (SELECT count(*) FROM clientes) AS clientes;
   ```

3. Debes ver **lab_20261117 | 4**. Los datos ya están cargados. Si alguna vez reinstalas el lab y falta la tabla, ejecuta todo [00-preparar.sql](00-preparar.sql) en esa misma consola. Si PostgreSQL está apagado, desde la raíz del proyecto basta con `docker compose -f compose.batch17.yaml up -d --wait`.
4. 
4. Abre la [guía 01](casos/01.md) y copia [el SQL del bug](casos/01.sql) a la consola. Trabaja sobre la copia para poder comparar con el original. Para los casos 07–10 selecciona **solo el bloque** que indique su guía.

## Cuando estés en blanco
 
| Paso | Qué haces con el teclado | Qué intentas averiguar |
| --- | --- | --- |
| 1. Define el resultado | Escribe un comentario como `-- Espero 4 filas, una por cliente`. | Qué representa **una fila** y qué ejemplo demostraría el fallo. |
| 2. Reproduce | Ejecuta el SQL inicial tal cual. Anota un dato concreto que difiera de tu predicción. | Qué hace hoy la consulta con esos mismos datos. |
| 3. Reduce | Empieza otra consulta con una sola tabla y el ID del ejemplo. Añade una unión, filtro o cálculo cada vez. | En qué paso cambia por primera vez el dato que seguías. |
| 4. Corrige | Escribe con tus palabras por qué cambió. Modifica solo esa parte en la copia del SQL inicial. | Si tu explicación predice lo que va a pasar al repetirla. |
| 5. Comprueba | Repite el caso que fallaba y uno que antes funcionaba. En transacciones, lee el estado después de confirmar o deshacer. | Si se cumple la regla en ambos casos. |

**Primera prueba del 01.** Escribe `SELECT id, nombre FROM sql_lab10.clientes WHERE id = 3;` en otra línea. Si ves `3 | Eva`, ya sabes que la ficha existe. Ahora escribe tú la consulta que busca los pedidos de ese mismo cliente. Apóyate en el mapa si dudas de la columna.

## Mapa de los datos

| Si buscas… | Mira en… | Una fila representa… |
| --- | --- | --- |
| Quién compra | `sql_lab10.clientes` | Un cliente identificado por `id`. |
| Qué pedido hizo | `sql_lab10.pedidos` | Un pedido. `cliente_id` indica quién lo hizo y `total` es el importe del pedido entero. |
| Qué productos lleva | `sql_lab10.lineas` | Un producto dentro de un pedido. `pedido_id` identifica el pedido y `unidades` la cantidad de ese producto. |
| Qué se puede vender | `sql_lab10.productos` y `sql_lab10.stock` | Un producto de catálogo y, cuando existe, su saldo físico y sus reservas. |
| Qué cambió tras una operación | `sql_lab10.caja` y `sql_lab10.envios` | Una cuenta o un envío del caso indicado por la columna `caso`. |

Para seguir **un cliente**, compara `clientes.id` con `pedidos.cliente_id`. Para abrir **un pedido** y ver sus productos, compara `pedidos.id` con `lineas.pedido_id`. Por ejemplo, el pedido `101` tiene dos líneas. Antes de sumar un importe, pregúntate si pertenece al pedido entero o a cada línea.

## Elige un caso

| Guía | Qué vas a investigar | Qué practicas |
| --- | --- | --- |
| [01 · Falta Eva](casos/01.md) | Un cliente sin pedidos desaparece del informe. | JOIN y conteo |
| [02 · Clientes sin compras](casos/02.md) | Una compra ajena afecta al resultado de otro cliente. | Subconsulta y `NOT EXISTS` |
| [03 · Unidades vendidas](casos/03.md) | Dos ventas pequeñas deberían alcanzar juntas el mínimo. | `GROUP BY`, suma y filtros |
| [04 · Página repetida](casos/04.md) | Un pedido aparece en dos páginas seguidas. | Orden, `LIMIT` y `OFFSET` |
| [05 · Stock bajo](casos/05.md) | El almacén cuenta unidades que ya están reservadas. | Cálculos, umbrales y filas ausentes |
| [06 · Fechas opcionales](casos/06.md) | Un informe queda vacío al omitir una fecha. | Parámetros, `NULL` y límites |
| [07 · Transferencia cancelada](casos/07.md) | Una de las cuentas cambia pese a cancelar la operación. | Atomicidad, `COMMIT` y `ROLLBACK` |
| [08 · Envío válido perdido](casos/08.md) | Al deshacer un envío se pierde otro válido. | `SAVEPOINT` y recuperación parcial |
| [09 · Operarios en espera](casos/09.md) | Dos personas trabajan en envíos distintos y se bloquean. | Bloqueos de filas y dos sesiones |
| [10 · Falta una retirada](casos/10.md) | Dos operaciones terminan con éxito y el saldo solo refleja una. | Concurrencia y actualización perdida |

Los diez casos se resuelven desde la misma conexión guardada en IntelliJ. En 09 y 10 abrirás dos consolas con sesiones distintas de esa misma base. Para seguir una petición real desde Swagger hasta el SQL, abre la [práctica de depuración en Spring Boot](SPRING.md).

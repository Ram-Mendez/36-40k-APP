# Diez bugs guiados de Spring Boot · INC-39 a INC-48

Abre `36-40k-APP`, reinicia **Backend APP** en Debug y entra en el [Swagger habitual](http://localhost:38088/swagger-ui/index.html). Autoriza con `operations` y abre **Conciliación de almacén**. Flyway prepara los datos en el esquema `warehouse_reconciliation` de `lab_20261117`.

Cada ticket indica la operación exacta, el primer breakpoint, los valores que debes mirar y la prueba que confirma el arreglo. Todas las peticiones son GET. Puedes repetirlas sin consumir los datos de práctica. Empieza por INC-43 si quieres probar el método con un cálculo que puedas verificar a mano.

| Ticket | Dificultad estimada sin guía | Incidencia |
| --- | --- | --- |
| [INC-39](INC-39-hecho25-11.md) | 8/10 | El saldo histórico ignora una salida registrada tarde |
| [INC-40](INC-40-hecho25-11.md) | 8/10 | Un lote pierde la vida útil mínima antes de llegar |
| [INC-41](INC-41-hecho25-11.md) | 9/10 | La preparación FIFO asigna menos unidades de las pedidas |
| [INC-42](INC-42-heco25-11.md) | 8/10 | El control de números de serie identifica mal el bulto sobrante |
| [INC-43](INC-43-hecho26-11.md) | 8/10 | El plan ofrece más kits de los que se pueden montar |
| [INC-44](INC-44-hecho26-11.md) | 9/10 | El segundo pedido usa stock que ya recibió el primero |
| [INC-45](INC-45-hecho26-11.md) | 8/10 | Dos muelles comparten la misma lista de transportistas |
| [INC-46](INC-46-mierdazo-de-bug-26-11.md) | 9/10 | La carga ignora un control pendiente que está dos pasos atrás |
| [INC-47](INC-47-hecho26-11-no-está-mal.md) | 8/10 | El peso facturable de varios bultos sale demasiado bajo |
| [INC-48](INC-48.md) | 8/10 | El cierre da por preparado un lote con un bulto sin pesar |

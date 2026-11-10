# Progresión del laboratorio backend

## Política vigente desde el 10 de noviembre de 2026

Solicitud del usuario: aumentar un poco la dificultad de TODO el lote tras resolver las incidencias de la madrugada y trabajar la feature y los merges. Aplicar desde el siguiente lote nuevo; no volver a romper ni sustituir el lote del 10/11, no modificar main ni corregir por él sus ramas.

Mantener exactamente cinco incidencias, una petición de negocio acotada y un ejercicio Git. Es una cola de trabajo, no una obligación diaria. Se incrementa la profundidad, no el volumen, la oscuridad de los enunciados ni el número de tecnologías.

### Incidencias

- Distribución objetivo: una medio-baja, tres medias y una medio-alta acotada. No es una clasificación salarial oficial ni un salto a nivel senior.
- Como máximo una incidencia resuelta únicamente reconociendo una inversión aislada de operador, dirección o literal. Evitar repetir los mecanismos del lote anterior con otras entidades.
- Al menos tres deben exigir relacionar dos evidencias o etapas del flujo: contrato HTTP, estado previo, operación de servicio, consulta/persistencia o resultado posterior. El arreglo puede ser pequeño; no exigir más archivos o líneas por decoración.
- Introducir una condición adicional relevante por ejercicio, no varias dificultades nuevas simultáneas: combinación de filtros y total paginado, cardinalidad de joins, frontera de fecha/importe, reintento con estado previo, rollback parcial o competencia reproducible.
- El ejercicio medio-alto trabaja UNA propiedad transaccional, de idempotencia, autorización por recurso o concurrencia del sistema existente; debe ser determinista y acotado. No añadir infraestructura distribuida por sí misma.
- Idempotencia necesita consolidación: no considerarla dominada porque exista un parche. El usuario escribió que no había entendido bien LAB-20261110-04; contrastar comprensión cuando la haya, sin transformar cada entrega en un examen teórico.

### Petición de negocio

Nivel medio y alcance pequeño: un comportamiento observable nuevo del dominio existente, una regla de negocio y criterios de aceptación para éxito, rechazo o frontera y compatibilidad con un comportamiento anterior. Debe requerir trabajo propio del usuario, no limitarse a aceptar código ya resuelto por team-a/team-b ni a añadir otro campo a una lista permitida. Los compañeros simulados pueden aportar contratos o cambios parciales reales, pero no entregar resuelta toda la feature del usuario.

### Git

Nivel medio: dos ramas con cambios reales y conservación explícita de ambos contratos. Rotar merge con conflicto controlado, cherry-pick/backport, revert o bisect, usando solo una maniobra nueva por lote. No fabricar conflicto todos los días ni dar la secuencia resuelta. Un merge sin marcadores no demuestra integración correcta: debe pasar la aceptación de A y de B.

### Testing y diagnóstico

Exigir evidencia de regresión antes/después cuando se ejecute, caso principal y una frontera pertinente, sin duplicar pruebas que ya cubran el contrato. Comprobar respuesta HTTP y estado persistido cuando proceda; para integración, PostgreSQL real. Pruebas de concurrencia coordinadas, no basadas solo en sleeps. No entregar anticipadamente los tests solución; sí contratos suficientes para construirlos. No desactivar aserciones ni confundir tests añadidos con tests ejecutados.

### Adaptación

Revisar main, las ramas de trabajo y los últimos diffs antes de cada lote. Mantener esta escala hasta observar evidencia suficiente para otro incremento. Un merge incompleto o una explicación pendiente orientan la siguiente práctica; no obligan a volver a tres incidencias triviales. No inferir autonomía, horas de trabajo efectivo ni dominio por fechas de commits.

## Revisión verificable del 10/11/2026

Horas Europe/Madrid; son marcas de commits, no medición de tiempo trabajado ni necesariamente horas de push o correo.

- Documentación inicial publicada en el commit `45ad067405c6e143f9f7b66f3061125045b4bd95`, fechado a las 00:10:27; código inicial `bcfd7035f0b82473649a3e11e33802122819066a`, sobre base `a6bc8058415a21c3dc75564974e375e69bdab9a9`.
- El registro del lote confirma correo enviado con messageId `1a1c296c1b6321bd`; no se ha consultado aquí la hora exacta de Gmail.
- Correcciones de las cinco incidencias y cuatro nuevos métodos de test presentes en `7039f8e93b583ddbabcf8a693259e36b719c56f7`, fechado a las 08:47:18. Estado: FIXES_PRESENT_NOT_EXECUTION_VERIFIED.
- Merge de team-a en `bc4b88b9825020c7163d068a2294b822732d9635`, a las 09:15:11; merge de team-b en `09da290cd689dad59c4065de0cba755acaafbd17`, a las 09:15:33.
- Main inicialmente observado en `b34b6bca203bf4cfdcb81ec734f3d69587855e31`; avanzó durante la revisión a `48a5b99440e4e6f3fbe125c822209d3499822b25`, que integra incidents. Releer el HEAD en futuras ejecuciones, no fijar esta revisión como base perpetua.
- En ese main, BusinessIntegrationTest conserva tanto `withdrawalKeepsItsNegativeQuantityInMovementHistory` como `movementHistoryReturnsNewestAdjustmentsFirstAcrossPages`.
- Hallazgo pendiente: `src/main/java/com/acme/operations/product/ProductService.java`, método list, admite id/name/unitPrice/sku pero no active. `PageRequests.of` rechaza órdenes fuera de la lista con 400 INVALID_PAGE. Por inspección estática, falta el contrato sort=active de team-b; no se ha ejecutado la petición HTTP aquí. No marcar CAT/GIT completos todavía ni arreglar main automáticamente.
- CI de ese main: check `jenkins-kind-lab`, id `129716591082`, run `42782787505`, IN_PROGRESS en la consulta, iniciado a las 09:27:31; sin conclusión disponible entonces. No se han ejecutado pruebas desde esta revisión.

Valoración de calibración, no certificación: LAB-01/02/03 son bajos por requerir filtro, dirección y estado puntuales; LAB-04/05 requieren razonamiento semántico de reintento y sesión. El incremento busca más diagnóstico y comprobación de contratos, no más trabajo mecánico.

## Fuentes del repositorio

- https://github.com/Ram-Mendez/36-40k-APP/commit/45ad067405c6e143f9f7b66f3061125045b4bd95
- https://github.com/Ram-Mendez/36-40k-APP/commit/7039f8e93b583ddbabcf8a693259e36b719c56f7
- https://github.com/Ram-Mendez/36-40k-APP/commit/09da290cd689dad59c4065de0cba755acaafbd17
- https://github.com/Ram-Mendez/36-40k-APP/commit/48a5b99440e4e6f3fbe125c822209d3499822b25
- https://github.com/Ram-Mendez/36-40k-APP/blob/48a5b99440e4e6f3fbe125c822209d3499822b25/src/main/java/com/acme/operations/product/ProductService.java
- https://github.com/Ram-Mendez/36-40k-APP/actions/runs/42782787505

Esta revisión modifica únicamente configuración/documentación del laboratorio. No entrega un lote nuevo, no cierra tickets del usuario y no sustituye la validación de ejecución.

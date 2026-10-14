# Auditoría del conversor XML a CSV y JSON

## 1. Tabla de los siete fallos

| Prueba | Qué pasa | Línea(s) del original | Categoría |
|---|---|---|---|
| F1 · El JSON es válido | El JSON generado no se puede leer porque se escriben comas finales después de las líneas y de los pedidos. | 56 y 58 | Formato |
| F2 · Una coma en un campo | Los campos del CSV se concatenan sin escapar las comas que contienen los datos, por lo que aparecen más columnas de las debidas. | 52-53 | Formato |
| F3 · Comillas y & en los datos | Los datos se insertan directamente en el JSON sin escapar las comillas ni otros caracteres especiales. Las comillas pueden romper la estructura JSON. | 49-50 y 55-56 | Formato |
| F4 · Fichero inexistente | Los ficheros de salida se abren antes de intentar leer el XML. El error se imprime, pero no se propaga al método que llama. | 25-30 y 65-66 | Recursos y excepciones |
| F5 · XML mal formado | Se crean o truncan los ficheros de salida antes de comprobar si el XML es válido. El error se captura y solo se imprime. | 25-30 y 65-66 | Recursos y excepciones |
| F6 · Pedido sin cliente | Se llama a `item(0)` aunque no exista ningún elemento `cliente`, lo que provoca una excepción que queda oculta. | 40 y 65-66 | Excepciones |
| F7 · Un fallo no pisa lo bueno | Al abrir los ficheros con `FileWriter` se sobrescribe su contenido anterior antes de saber si la conversión puede realizarse. | 25-26 y 28-30 | Recursos |

## 2. Orden de apertura de los ficheros y lectura del XML

Los dos ficheros de salida se abren antes de leer el XML. Al crear un `FileWriter` sobre un fichero existente, su contenido se trunca. Por tanto, si el XML está roto o no existe, un `pedidos.csv` válido de una conversión anterior puede quedar vacío o perder su contenido.

Para evitarlo, primero hay que leer y analizar el XML correctamente y abrir los ficheros de salida después. Así, si la entrada no es válida, no se destruyen las salidas anteriores.

## 3. El bloque `catch`

El bloque `catch (Exception e)` captura cualquier excepción y ejecuta `e.printStackTrace()`. Esto muestra información del error en la consola, pero no vuelve a lanzar la excepción. Por eso, el código que llama a `convertir` puede creer que la conversión ha terminado sin problemas.

Además, capturar `Exception` de forma tan general dificulta distinguir los tipos de error y tratarlos de manera específica. En este caso, hay que propagar el error mediante una excepción que incluya el nombre del fichero de entrada y la causa original.

## 4. Cierre de los ficheros

Los ficheros se cierran al final del bloque `try`, en las líneas 62 y 63. Si se produce una excepción antes de llegar a esas instrucciones, los escritores pueden quedarse abiertos porque el cierre no está garantizado.

Una forma de solucionarlo es utilizar `try-with-resources`. Esta construcción cierra automáticamente los recursos al salir del bloque, incluso cuando se produce una excepción.

## 5. Pedido sin cliente

Si el pedido no contiene ningún elemento `cliente`, `getElementsByTagName("cliente").item(0)` devuelve `null`. Al llamar después a `getTextContent()` sobre ese valor, se produce una `NullPointerException`.

El programa parece terminar sin comunicar correctamente el fallo porque la excepción queda capturada por el `catch`, que solo imprime la traza y no la propaga al método que llamó a `convertir`.

## 6. Problemas en la generación del JSON

Hay al menos dos motivos por los que el JSON generado puede ser inválido:

1. Se añade `, ` después de cada línea de pedido y `},` después de cada pedido. La última entrada de una lista JSON no puede terminar con una coma.
2. Los valores de texto se concatenan directamente dentro de cadenas JSON. Si un cliente o producto contiene comillas dobles, barras invertidas o saltos de línea, esos caracteres deben escaparse correctamente.

Por eso, aunque los datos sean normales, las comas finales pueden hacer que el documento no sea JSON válido. Además, determinados caracteres en los datos pueden romper su estructura.

## 7. Comas y comillas en los datos

En el CSV, una coma dentro del nombre `Talleres "O Rápido", S.L.` se interpreta como un separador de columnas porque el programa concatena los campos sin escaparlos. Para cumplir las reglas del CSV, el campo debe ir entre comillas dobles y las comillas que contenga deben duplicarse.

En el JSON, las comillas del nombre se insertan directamente dentro de una cadena delimitada por comillas dobles. Esto puede cerrar la cadena antes de tiempo y hacer que el documento sea inválido. En JSON, las comillas dobles y otros caracteres especiales deben escaparse con las reglas correspondientes.

## 8. Codificación de los ficheros

Los constructores `new FileWriter(salidaCsv)` y `new FileWriter(salidaJson)` utilizan la codificación predeterminada de la plataforma Java, en lugar de especificar una codificación de forma explícita.

En Java 21, UTF-8 es la codificación predeterminada en las configuraciones estándar, por lo que no necesariamente se observará un fallo al ejecutar el programa en el aula. Sin embargo, depender de una codificación implícita hace que el comportamiento sea menos explícito y puede causar problemas de compatibilidad en otros entornos o con herramientas que esperen otra codificación.

La solución recomendable es indicar explícitamente UTF-8 al escribir los ficheros y utilizar la misma codificación al leerlos.

## Cambios realizados en la copia `tocar/`

**Cambio 1: gestión de errores y apertura de salidas.** El XML se analiza antes de abrir los ficheros de salida. Además, si ocurre un error, se propaga una excepción que incluye el nombre del fichero de entrada. De este modo, el error no queda oculto y una entrada inválida no crea ficheros vacíos.

**Cambio 2: escape de campos CSV.** Se ha añadido un método `csv(String campo)` que pone entre comillas los campos que contienen comas, comillas o saltos de línea y duplica las comillas internas. El método se utiliza al escribir los campos de cliente y producto.

**Comprobación:** se ha quitado `@Disabled` de `Actividad4Test.java` y se ha ejecutado `mvn test -Dtest=Actividad4Test`. El resultado ha sido `Tests run: 3, Failures: 0, Errors: 0, Skipped: 0` y `BUILD SUCCESS`.
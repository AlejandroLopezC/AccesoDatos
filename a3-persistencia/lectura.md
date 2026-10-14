# Actividad 3 — Lectura del código de persistencia

## 1. Genéricos e interfaz (`RepositorioJson.java`, línea 18)

`<T extends Identificable>` indica que la clase puede trabajar con diferentes tipos de objetos, siempre que implementen la interfaz `Identificable`. `RepositorioJson` conoce los detalles del almacenamiento en JSON, mientras que el resto de la aplicación puede usar la interfaz `Repositorio` sin depender de cómo se guardan los datos.

## 2. Configuración del `ObjectMapper` (`RepositorioJson.java`, líneas 22-25)

`registerModule(new JavaTimeModule())` permite trabajar con tipos de fecha y hora de Java. `disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)` hace que las fechas se guarden como texto en lugar de números. `enable(SerializationFeature.INDENT_OUTPUT)` organiza el JSON con sangría para que sea más fácil de leer. Si quitamos `JavaTimeModule`, puede producirse un error al serializar o deserializar las fechas. Hay que ejecutar `mvn test` para comprobar el mensaje exacto.

## 3. Tipo de la lista (`RepositorioJson.java`, línea 29)

No basta con usar `List.class` porque se pierde la información del tipo de objeto que contiene la lista. Al leer el JSON, Jackson podría crear mapas genéricos en lugar de objetos `Reserva`. El problema aparecería después, al intentar utilizar esos elementos como reservas. Por eso se construye un `JavaType` con el tipo concreto de la lista.

## 4. Fichero inexistente o incompleto (`RepositorioJson.java`, línea 34)

Si el fichero todavía no existe, devolver una lista vacía permite que la aplicación funcione en su primera ejecución, antes de que se haya guardado ninguna reserva. En cambio, si el fichero existe pero su contenido está cortado o tiene un JSON incorrecto, debe producirse una excepción de persistencia, porque los datos no se pueden interpretar correctamente.

## 5. Excepciones al leer (`RepositorioJson.java`, líneas 37-40)

`JsonProcessingException` indica que Jackson no ha podido interpretar correctamente el contenido JSON. `IOException` recoge otros problemas de entrada y salida, como dificultades para leer el fichero. El primer `catch` debe ir antes porque `JsonProcessingException` hereda de `IOException`. Así se puede tratar el error específico antes que el más general.

## 6. Escritura segura (`RepositorioJson.java`, líneas 50-53)

Primero se crea un fichero temporal en la misma carpeta que el destino. Después se escriben en él los datos JSON y, por último, se mueve para sustituir el fichero original mediante `ATOMIC_MOVE` y `REPLACE_EXISTING`. Si se interrumpe el proceso antes del movimiento, el fichero original debería conservarse y podría quedar el temporal. Si se escribiera directamente sobre `reservas.json`, un fallo durante la escritura podría dejar el fichero original incompleto o dañado.

## 7. Temporal en el mismo disco (`RepositorioJson.java`, líneas 48-53)

El temporal se crea en la misma carpeta que el fichero de destino para facilitar el movimiento atómico. Si estuviera en otro disco o sistema de archivos, el movimiento atómico podría no ser compatible y lanzar una `IOException`. El problema se detectaría en el `catch` de `guardarTodos()`, que transforma el error en una `PersistenciaException`.

## 8. Bloque `finally` (`RepositorioJson.java`, líneas 56-58)

El bloque `finally` intenta borrar el fichero temporal tanto si el guardado termina correctamente como si ocurre una excepción. Si `Files.move` ya ha movido el temporal, puede que ya no exista nada que borrar. El `try-catch` interior evita que un fallo al borrar el temporal oculte el resultado del guardado.

## 9. Anotaciones XML (`RespaldoXml.java`, líneas 19-23)

`@JacksonXmlRootElement(localName = "reservas")` establece el nombre del elemento raíz del XML. `@JacksonXmlElementWrapper(useWrapping = false)` evita añadir un elemento contenedor adicional alrededor de la lista. `@JacksonXmlProperty(localName = "reserva")` establece el nombre de cada elemento de la lista. Si quitamos la segunda anotación, cambia la estructura del XML y puede fallar la prueba que comprueba la exportación e importación de las reservas. El número exacto de esa prueba debe comprobarse ejecutando los tests.

## 10. Caracteres especiales y lista nula (`RespaldoXml.java`, líneas 37 y 47)

Al escribir el XML, `XmlMapper` se encarga de escapar los caracteres especiales, por ejemplo convierte `&` en `&amp;`. Al volver a leerlo, el analizador XML recupera el carácter original. Si `r.reserva()` es `null`, el método devuelve `List.of()`, que representa una lista vacía en lugar de devolver `null`. Para indicar qué prueba concreta cubre este caso, hay que comprobar los métodos de `RespaldoXmlTest.java`.
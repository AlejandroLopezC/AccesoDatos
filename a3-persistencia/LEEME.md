# a3-persistencia · Actividad 3 de AD-UD1

Persistencia de las reservas del taller en JSON, con escritura segura y exportación e importación mediante XML.

## Estructura del proyecto

- `src/main/java/ad/taller/`: clases `Reserva`, `Identificable`, `Repositorio`, `RepositorioJson`, `RespaldoXml` y `PersistenciaException`.
- `src/test/java/ad/taller/`: pruebas de `RepositorioJson`, `RespaldoXml` y `Actividad3Test`.
- `reservas.json`: fichero JSON de reservas del taller.

## Cambios realizados

1. **Respaldo JSON:** antes de sustituir un fichero JSON existente, se conserva su contenido en un archivo `.bak`. En el primer guardado no se crea el respaldo porque no existe una versión anterior.
2. **Control de IDs duplicados en XML:** la importación comprueba que no haya identificadores repetidos y lanza una `PersistenciaException` si encuentra uno.

## Comprobación

Se ha ejecutado `mvn clean test` con el siguiente resultado:

- `Actividad3Test`: 4 pruebas superadas.
- `RepositorioJsonTest`: 4 pruebas superadas.
- `RespaldoXmlTest`: 4 pruebas superadas.
- **Total: 12 pruebas, 0 fallos, 0 errores y 0 omitidas.**
- Resultado de Maven: `BUILD SUCCESS`.

## Ejecución de las pruebas

Desde la carpeta del proyecto:

```bash
mvn clean test
```
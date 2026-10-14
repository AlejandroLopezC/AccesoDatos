
package ad.ud1;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.*;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.stream.Stream;

/**
 * Auditor de directorios.
 *
 * Uso:
 *   mvn -q compile exec:java
 *   mvn -q compile exec:java -Dexec.args="RUTA"
 *   mvn -q compile exec:java -Dexec.args="RUTA -r"
 *
 * Sin ruta se utiliza el directorio actual.
 */
public class Explorador {

    private static final DateTimeFormatter FECHA =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                    .withZone(ZoneId.systemDefault());

    private static int ficheros = 0;
    private static int directorios = 0;
    private static int sinAcceso = 0;
    private static long bytes = 0;

    public static void main(String[] args) {
        String textoRuta = ".";
        boolean recursivo = false;

        // 1. Leer los argumentos.
        for (String argumento : args) {
            if (argumento.equals("-r")) {
                recursivo = true;
            } else {
                textoRuta = argumento;
            }
        }

        // 2. Construir y normalizar la ruta absoluta.
        Path raiz = Path.of(textoRuta).toAbsolutePath().normalize();

        System.out.println("Explorando " + raiz);

        // 3. Comprobar que la ruta existe.
        if (!Files.exists(raiz)) {
            System.err.println("ERROR: no existe " + raiz);
            System.exit(1);
            return;
        }

        // Si la ruta es un fichero, mostrar solo sus datos.
        if (!Files.isDirectory(raiz)) {
            Path base = raiz.getParent() == null
                    ? raiz
                    : raiz.getParent();

            System.out.println(linea(raiz, base));
            resumen();

            if (sinAcceso > 0) {
                System.exit(1);
            }
            return;
        }

        int codigo = 0;

        // 4. Listar un nivel o recorrer el árbol completo.
        try (Stream<Path> rutas = recursivo
                ? Files.walk(raiz).skip(1)
                : Files.list(raiz)) {

            // En modo recursivo no ordenamos el Stream.
            Stream<Path> listado = recursivo
                    ? rutas
                    : rutas.sorted();

            listado.forEach(p ->
                    System.out.println(linea(p, raiz)));

        } catch (NoSuchFileException e) {
            System.err.println(
                    "ERROR: la ruta ha desaparecido: " + e.getFile());
            codigo = 1;

        } catch (NotDirectoryException e) {
            System.err.println(
                    "ERROR: no es un directorio: " + e.getFile());
            codigo = 1;

        } catch (AccessDeniedException e) {
            System.err.println(
                    "ERROR: sin permiso para acceder a "
                            + e.getFile());
            sinAcceso++;
            codigo = 1;

        } catch (UncheckedIOException e) {
            Throwable causa = e.getCause();

            System.err.println(
                    "ERROR: recorrido interrumpido: "
                            + causa.getMessage());

            // Files.walk puede fallar durante el recorrido,
            // cuando el Stream ya se está consumiendo.
            codigo = 1;

        } catch (IOException e) {
            System.err.println(
                    "ERROR de E/S en " + raiz + ": "
                            + e.getMessage());
            codigo = 1;
        }

        // 5. Mostrar el resumen.
        resumen();

        if (codigo != 0 || sinAcceso > 0) {
            System.exit(1);
        }
    }

    /**
     * Construye la línea con los permisos, tamaño, fecha y nombre.
     * Captura los fallos de cada elemento para no lanzarlos desde
     * la lambda del recorrido.
     */
    static String linea(Path p, Path base) {
        try {
            boolean esDirectorio = Files.isDirectory(p);

            String permisos =
                    (Files.isReadable(p) ? "r" : "-")
                    + (Files.isWritable(p) ? "w" : "-")
                    + (Files.isExecutable(p) ? "x" : "-");

            // Para directorios mostramos tamaño cero.
            long tamano = esDirectorio ? 0 : Files.size(p);

            String fecha = FECHA.format(
                    Files.getLastModifiedTime(p).toInstant());

            // Los hijos directos no llevan sangría.
            // Cada nivel adicional añade dos espacios.
            int nivel = base.relativize(p).getNameCount();
            String sangria = "  ".repeat(Math.max(0, nivel - 1));

            Path ultimoNombre = p.getFileName();

            String nombre = ultimoNombre == null
                    ? p.toString()
                    : ultimoNombre.toString();

            if (esDirectorio) {
                directorios++;
                nombre += "/";
            } else {
                ficheros++;
                bytes += tamano;
            }

            return sangria
                    + String.format("%s %8d %s %s",
                    permisos, tamano, fecha, nombre);

        } catch (IOException e) {
            sinAcceso++;

            Path relativa;
            try {
                relativa = base.relativize(p);
            } catch (IllegalArgumentException ex) {
                relativa = p;
            }

            return relativa
                    + " [sin acceso: "
                    + e.getClass().getSimpleName()
                    + ": " + e.getMessage() + "]";
        }
    }

    /**
     * Imprime el resumen del recorrido.
     */
    private static void resumen() {
        System.out.printf(
                "%n%d ficheros · %d directorios · %d bytes · "
                        + "%d sin acceso%n",
                ficheros, directorios, bytes, sinAcceso);
    }

    /**
     * PRUEBAS DEL PASO 9
     *
     * PRUEBA 1: listado de un nivel.
     *
     * Comando:
     * mvn -q compile exec:java
     *
     * Salida obtenida:
     * Explorando C:\Users\DAM.PC-01\Desktop\AD-UD1-soluciones\a1-explorador
     * rwx      546 2026-10-13 11:18 pom.xml
     * rwx        0 2026-10-13 13:42 src/
     * rwx        0 2026-10-13 13:48 target/
     *
     * 1 ficheros · 2 directorios · 546 bytes · 0 sin acceso
     *
     *
     * PRUEBA 2: recorrido recursivo.
     *
     * Comando:
     * mvn -q compile exec:java -Dexec.args="-r"
     *
     * Salida obtenida:
     * Explorando C:\Users\DAM.PC-01\Desktop\AD-UD1-soluciones\a1-explorador
     * rwx      546 2026-10-13 11:18 pom.xml
     * rwx        0 2026-10-13 13:42 src/
     *   rwx        0 2026-10-13 13:42 main/
     *     rwx        0 2026-10-13 13:42 java/
     *       rwx        0 2026-10-13 13:42 ad/
     *         rwx        0 2026-10-13 13:42 ud1/
     *           rwx     7030 2026-10-13 13:53 Explorador.java
     * rwx        0 2026-10-13 13:48 target/
     *   rwx     5155 2026-10-13 13:48 a1-explorador-1.0.jar
     *   rwx        0 2026-10-13 13:48 classes/
     *     rwx        0 2026-10-13 13:54 ad/
     *       rwx        0 2026-10-13 13:54 ud1/
     *         rwx     7767 2026-10-13 13:54 Explorador.class
     *   rwx        0 2026-10-13 13:48 generated-sources/
     *     rwx        0 2026-10-13 13:48 annotations/
     *   rwx        0 2026-10-13 13:48 maven-archiver/
     *     rwx       55 2026-10-13 13:48 pom.properties
     *   rwx        0 2026-10-13 13:48 maven-status/
     *     rwx        0 2026-10-13 13:48 maven-compiler-plugin/
     *       rwx        0 2026-10-13 13:48 compile/
     *         rwx        0 2026-10-13 13:48 default-compile/
     *           rwx       24 2026-10-13 13:54 createdFiles.lst
     *           rwx       97 2026-10-13 13:54 inputFiles.lst
     *
     * 7 ficheros · 16 directorios · 20674 bytes · 0 sin acceso
     *
     *
     * PRUEBA 3: ruta inexistente.
     *
     * Comando:
     * mvn -q compile exec:java -Dexec.args="carpeta_que_no_existe"
     *
     * Salida obtenida:
     * Explorando C:\Users\DAM.PC-01\Desktop\AD-UD1-soluciones\a1-explorador\carpeta_que_no_existe
     * ERROR: no existe C:\Users\DAM.PC-01\Desktop\AD-UD1-soluciones\a1-explorador\carpeta_que_no_existe
     *
     *
     * PRUEBA 4: un fichero en lugar de un directorio.
     *
     * Comando:
     * mvn -q compile exec:java -Dexec.args="pom.xml"
     *
     * Salida obtenida:
     * Explorando C:\Users\DAM.PC-01\Desktop\AD-UD1-soluciones\a1-explorador\pom.xml
     * rwx      546 2026-10-13 11:18 pom.xml
     *
     * 1 ficheros · 0 directorios · 546 bytes · 0 sin acceso
     *
     *
     * NOTA:
     * Las fechas, tamaños y resultados corresponden a las ejecuciones
     * realizadas en el equipo. Pueden cambiar si se modifican archivos
     * o se vuelve a compilar el proyecto.
     */
}

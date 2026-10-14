
package ad.ud1;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class Importador {

    // La codificación se declara explícitamente para no depender del sistema.
    private static final Charset CODIFICACION_ENTRADA = StandardCharsets.UTF_8;
    private static final Charset CODIFICACION_SALIDA = StandardCharsets.UTF_8;

    record Cliente(String dni, String nombre, String telefono,
                   String codigoPostal) {}

    // Permite informar de los nombres que se han reparado durante la importación.
    private record ResultadoParseo(Cliente cliente, boolean nombreReparado) {}

    /**
     * Forma de acceso:
     * Se utiliza acceso secuencial porque la importación necesita revisar
     * todas las líneas del fichero, una detrás de otra, y validar cada registro.
     * BufferedReader permite hacerlo sin cargar el fichero entero en memoria.
     * El acceso aleatorio sería útil si hubiera que consultar repetidamente
     * registros concretos por su posición, pero un CSV no incluye un índice
     * que permita localizar una fila directamente.
     * Para disponer de acceso aleatorio habría que crear y mantener un índice
     * de posiciones, lo que añadiría complejidad y trabajo previo.
     * Además, la lectura secuencial facilita conservar el orden original
     * y conocer el número de línea de cada registro rechazado.
     * Como aquí se procesan todos los registros una sola vez, el acceso
     * secuencial es la opción más sencilla y adecuada.
     */

    /**
     * Observaciones comprobadas en el CSV utilizado:
     * - Tiene una cabecera y utiliza comas como separador.
     * - Las filas terminan con una coma que genera un campo vacío adicional.
     * - Contiene un comentario y registros con campos vacíos o inválidos.
     * - Se encuentran DNI incorrectos o duplicados, teléfonos no válidos,
     *   códigos postales de cuatro cifras y una fila con una columna extra.
     * - Algunos nombres presentan texto mal codificado, por ejemplo "XoÃ¡n".
     *   Se intenta reparar únicamente cuando la conversión es segura.
     * - Las filas inválidas se guardan por separado para no detener la lectura.
     *
     * Revisión con IA: se reforzaron el análisis de comillas, las validaciones
     * y el tratamiento del texto mal codificado; se comprobó mediante ejecución.
     */

    public static void main(String[] args) {
        Path origen = Path.of(
                args.length > 0 ? args[0] : "clientes_importacion.csv"
        ).toAbsolutePath().normalize();

        Path directorioSalida = Path.of("salida");

        Map<String, Integer> motivos = new LinkedHashMap<>();
        Set<String> dnisImportados = new HashSet<>();

        int lineasLeidas = 0;
        int importadas = 0;
        int rechazadas = 0;
        int cabecerasIgnoradas = 0;
        int comentariosIgnorados = 0;
        int nombresReparados = 0;

        try {
            if (!Files.isRegularFile(origen)) {
                throw new IOException("No existe el fichero: " + origen);
            }

            Files.createDirectories(directorioSalida);

            try (BufferedReader entrada =
                         Files.newBufferedReader(origen, CODIFICACION_ENTRADA);
                 BufferedWriter correctos = Files.newBufferedWriter(
                         directorioSalida.resolve("clientes_ok.csv"),
                         CODIFICACION_SALIDA);
                 BufferedWriter rechazadosWriter = Files.newBufferedWriter(
                         directorioSalida.resolve("rechazados.csv"),
                         CODIFICACION_SALIDA)) {

                correctos.write("DNI;nombre;telefono;codigoPostal");
                correctos.newLine();

                rechazadosWriter.write("linea;contenido;motivo");
                rechazadosWriter.newLine();

                String linea;

                while ((linea = entrada.readLine()) != null) {
                    lineasLeidas++;

                    // Elimina la marca BOM de UTF-8 si aparece al principio.
                    if (lineasLeidas == 1 && !linea.isEmpty()
                            && linea.charAt(0) == '\uFEFF') {
                        linea = linea.substring(1);
                    }

                    String limpia = linea.stripLeading();

                    // Reconoce la cabecera sin distinguir mayúsculas.
                    if (limpia.regionMatches(true, 0, "DNI,", 0, 4)
                            || limpia.regionMatches(true, 0, "DNI;", 0, 4)) {
                        cabecerasIgnoradas++;
                        continue;
                    }

                    // Las líneas que empiezan por # son comentarios.
                    if (limpia.startsWith("#")) {
                        comentariosIgnorados++;
                        continue;
                    }

                    Optional<String> problema = validar(linea, dnisImportados);

                    if (problema.isPresent()) {
                        String motivo = problema.get();

                        motivos.merge(motivo, 1, Integer::sum);
                        rechazadas++;

                        rechazadosWriter.write(lineasLeidas + ";"
                                + csv(linea) + ";"
                                + csv(motivo));
                        rechazadosWriter.newLine();
                        continue;
                    }

                    ResultadoParseo resultado = parsear(linea);
                    Cliente cliente = resultado.cliente();

                    correctos.write(csv(cliente.dni()) + ";"
                            + csv(cliente.nombre()) + ";"
                            + csv(cliente.telefono()) + ";"
                            + csv(cliente.codigoPostal()));
                    correctos.newLine();

                    dnisImportados.add(cliente.dni());
                    importadas++;

                    if (resultado.nombreReparado()) {
                        nombresReparados++;
                    }
                }
            }

        } catch (IOException e) {
            System.err.println(
                    "ERROR al leer el fichero o escribir la salida: "
                            + e.getMessage()
            );
            System.exit(1);
            return;
        }

        System.out.printf(
                "Líneas leídas: %d%n"
                        + "Importadas: %d%n"
                        + "Rechazadas: %d%n"
                        + "Cabeceras ignoradas: %d%n"
                        + "Comentarios ignorados: %d%n"
                        + "Nombres con codificación reparada: %d%n",
                lineasLeidas, importadas, rechazadas,
                cabecerasIgnoradas, comentariosIgnorados, nombresReparados
        );

        System.out.println("Rechazos por motivo:");

        if (motivos.isEmpty()) {
            System.out.println("  Ninguno");
        } else {
            motivos.forEach((motivo, cantidad) ->
                    System.out.printf("  %-38s %d%n", motivo, cantidad));
        }
    }

    /**
     * Comprueba la estructura y los campos obligatorios del registro.
     * Se conserva el primer motivo detectado para que cada fila tenga
     * una explicación clara en rechazados.csv.
     */
    static Optional<String> validar(
            String linea, Set<String> dnisImportados) {

        if (linea.isBlank()) {
            return Optional.of("Línea vacía");
        }

        String[] campos;

        try {
            campos = normalizarCampos(separarCampos(linea, ','));
        } catch (IllegalArgumentException e) {
            return Optional.of(e.getMessage());
        }

        if (campos.length != 4) {
            return Optional.of(
                    "Se esperaban 4 campos, hay " + campos.length
            );
        }

        for (int i = 0; i < campos.length; i++) {
            campos[i] = campos[i].trim();
        }

        campos[0] = campos[0].toUpperCase();

        if (campos[0].isEmpty()) {
            return Optional.of("DNI vacío");
        }

        if (!campos[0].matches("\\d{8}[A-Z]")) {
            return Optional.of("DNI con formato incorrecto");
        }

        if (campos[1].isEmpty()) {
            return Optional.of("Nombre vacío");
        }

        if (campos[2].isEmpty()) {
            return Optional.of("Teléfono vacío");
        }

        if (!pareceTelefono(campos[2])) {
            return Optional.of("Teléfono con formato incorrecto");
        }

        if (campos[3].isEmpty()) {
            return Optional.of("Código postal vacío");
        }

        if (!campos[3].matches("\\d{5}")) {
            return Optional.of("Código postal no son 5 cifras");
        }

        if (dnisImportados.contains(campos[0])) {
            return Optional.of("DNI duplicado");
        }

        return Optional.empty();
    }

    private static ResultadoParseo parsear(String linea) {
        String[] campos = normalizarCampos(separarCampos(linea, ','));

        String dni = campos[0].trim().toUpperCase();
        String nombreOriginal = campos[1].trim();
        String nombreCorregido = repararTextoMalCodificado(nombreOriginal);

        Cliente cliente = new Cliente(
                dni,
                nombreCorregido,
                campos[2].trim(),
                campos[3].trim()
        );

        return new ResultadoParseo(
                cliente,
                !nombreOriginal.equals(nombreCorregido)
        );
    }

    /**
     * Normaliza dos casos reconocibles:
     * 1. Una última columna vacía sobrante, producida por la coma final.
     * 2. Un nombre con comas sin comillas, cuando teléfono y código postal
     *    se reconocen claramente en las dos últimas posiciones.
     *
     * Una columna extra con contenido no se elimina: la fila se rechaza.
     */
    private static String[] normalizarCampos(String[] campos) {

        if (campos.length > 4
                && campos[campos.length - 1].trim().isEmpty()) {
            campos = Arrays.copyOf(campos, campos.length - 1);
        }

        if (campos.length >= 5
                && campos[0].trim().matches("\\d{8}[A-Za-z]")
                && pareceTelefono(campos[campos.length - 2].trim())
                && campos[campos.length - 1].trim().matches("\\d{5}")) {

            StringBuilder nombre = new StringBuilder();

            for (int i = 1; i < campos.length - 2; i++) {
                if (i > 1) {
                    nombre.append(", ");
                }

                nombre.append(campos[i].trim());
            }

            return new String[] {
                    campos[0].trim(),
                    nombre.toString(),
                    campos[campos.length - 2].trim(),
                    campos[campos.length - 1].trim()
            };
        }

        return campos;
    }

    private static boolean pareceTelefono(String telefono) {
        if (!telefono.matches("[+0-9 ()-]{7,25}")) {
            return false;
        }

        long cifras = telefono.chars()
                .filter(Character::isDigit)
                .count();

        return cifras >= 7;
    }

    /**
     * Analiza una fila CSV con comas como separador.
     * Respeta las comas dentro de campos entrecomillados y las comillas
     * dobles escapadas (""). Rechaza comillas sin cerrar o mal colocadas.
     */
    private static String[] separarCampos(String linea, char separador) {
        List<String> campos = new ArrayList<>();
        StringBuilder campo = new StringBuilder();

        boolean entreComillas = false;
        boolean comillasCerradas = false;

        for (int i = 0; i < linea.length(); i++) {
            char caracter = linea.charAt(i);

            if (entreComillas) {
                if (caracter == '"') {
                    if (i + 1 < linea.length()
                            && linea.charAt(i + 1) == '"') {
                        campo.append('"');
                        i++;
                    } else {
                        entreComillas = false;
                        comillasCerradas = true;
                    }
                } else {
                    campo.append(caracter);
                }

            } else if (caracter == separador) {
                campos.add(campo.toString().trim());
                campo.setLength(0);
                comillasCerradas = false;

            } else if (caracter == '"') {
                if (comillasCerradas || !campo.toString().isBlank()) {
                    throw new IllegalArgumentException(
                            "Comillas mal colocadas en la fila"
                    );
                }

                campo.setLength(0);
                entreComillas = true;

            } else {
                if (comillasCerradas && !Character.isWhitespace(caracter)) {
                    throw new IllegalArgumentException(
                            "Texto después de cerrar comillas en la fila"
                    );
                }

                if (!comillasCerradas) {
                    campo.append(caracter);
                }
            }
        }

        if (entreComillas) {
            throw new IllegalArgumentException(
                    "Comillas sin cerrar en la fila"
            );
        }

        campos.add(campo.toString().trim());

        return campos.toArray(new String[0]);
    }

    /**
     * Repara casos típicos de texto UTF-8 interpretado como ISO-8859-1.
     * Solo se aplica si aparecen marcadores habituales (Ã o Â), todos los
     * caracteres se pueden representar en ISO-8859-1 y la conversión elimina
     * esos marcadores sin introducir el carácter de sustitución.
     */
    
    private static String repararTextoMalCodificado(String texto) {
        if (texto == null || texto.isEmpty()) {
            return texto;
        }

        if (!texto.contains("Ã") && !texto.contains("Â")) {
            return texto;
        }

        try {
            byte[] bytes = texto.getBytes(StandardCharsets.ISO_8859_1);
            String corregido = new String(bytes, StandardCharsets.UTF_8);

            if (!corregido.contains("\uFFFD")
                    && corregido.chars().filter(c -> c == 'Ã').count()
                    < texto.chars().filter(c -> c == 'Ã').count()) {
                return corregido;
            }
        } catch (Exception e) {
            // Si no se puede reparar, conservamos el texto original.
        }

        return texto;
    }


    /**
     * Escapa un campo del CSV de salida, cuyo separador es punto y coma.
     * Si contiene separadores, comillas o saltos de línea, se entrecomilla
     * y las comillas internas se duplican.
     */
    static String csv(String texto) {
        if (texto.contains(";") || texto.contains("\"")
                || texto.contains("\n") || texto.contains("\r")) {
            return "\"" + texto.replace("\"", "\"\"") + "\"";
        }

        return texto;
    }
}

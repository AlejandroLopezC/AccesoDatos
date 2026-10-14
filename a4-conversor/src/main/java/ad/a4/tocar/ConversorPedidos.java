package ad.a4.tocar;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class ConversorPedidos {

    public static void main(String[] args) {
        String entrada = args.length > 0 ? args[0] : "pedidos.xml";
        convertir(entrada, "pedidos.csv", "pedidos.json");
    }

    public static void convertir(String entrada, String salidaCsv, String salidaJson) {
        try {
            // Primero leemos y validamos el XML.
            // Todavía no abrimos los archivos de salida.
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new File(entrada));

            NodeList pedidos = doc.getElementsByTagName("pedido");

            // Comprobamos que todos los pedidos tengan cliente.
            for (int i = 0; i < pedidos.getLength(); i++) {
                Element pedido = (Element) pedidos.item(i);
                NodeList clientes = pedido.getElementsByTagName("cliente");

                if (clientes.getLength() == 0) {
                    throw new IOException(
                        "El pedido " + pedido.getAttribute("id") + " no tiene cliente."
                    );
                }
            }

            // Solo abrimos los archivos cuando el XML se ha podido leer.
            try (FileWriter csv = new FileWriter(salidaCsv);
                 FileWriter json = new FileWriter(salidaJson)) {

                csv.write("pedido,fecha,cliente,producto,cantidad,precio\n");
                json.write("[\n");

                for (int i = 0; i < pedidos.getLength(); i++) {
                    Element pedido = (Element) pedidos.item(i);
                    String id = pedido.getAttribute("id");
                    String fecha = pedido.getAttribute("fecha");
                    String cliente = pedido.getElementsByTagName("cliente")
                                            .item(0).getTextContent();

                    json.write("  {\"id\": \"" + id + "\", \"fecha\": \"" + fecha
                            + "\", \"cliente\": \"" + cliente + "\", \"lineas\": [");

                    NodeList lineas = pedido.getElementsByTagName("linea");

                    for (int j = 0; j < lineas.getLength(); j++) {
                        Element linea = (Element) lineas.item(j);
                        String producto = linea.getAttribute("producto");
                        int cantidad = Integer.parseInt(linea.getAttribute("cantidad"));
                        double precio = Double.parseDouble(linea.getAttribute("precio"));

                        // Escapamos los campos de texto del CSV.
                        csv.write(csv(id) + "," + csv(fecha) + "," + csv(cliente) + ","
                                + csv(producto) + "," + cantidad + "," + precio + "\n");

                        json.write("{\"producto\": \"" + producto + "\", \"cantidad\": "
                                + cantidad + ", \"precio\": " + precio + "}, ");
                    }

                    json.write("]},\n");
                }

                json.write("]\n");
            }

            System.out.println("Conversión completada.");

        } catch (Exception e) {
            throw new RuntimeException(
                "No se pudo convertir el archivo '" + entrada + "': " + e.getMessage(), e
            );
        }
    }

    // Si el campo contiene coma, comillas o salto de línea,
    // lo encerramos entre comillas y duplicamos las comillas internas.
    private static String csv(String campo) {
        if (campo.contains(",") || campo.contains("\"")
                || campo.contains("\n") || campo.contains("\r")) {
            return "\"" + campo.replace("\"", "\"\"") + "\"";
        }
        return campo;
    }
}
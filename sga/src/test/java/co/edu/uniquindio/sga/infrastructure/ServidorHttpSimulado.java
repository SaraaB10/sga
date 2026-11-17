package co.edu.uniquindio.sga.infrastructure;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Servidor HTTP simulado en 127.0.0.1 con un puerto libre, para probar los adaptadores externos (Gemini, Cloudinary)
 * sin salir a internet. Responde en orden las respuestas que se le encolan (la última se repite) y guarda cada
 * petición recibida.
 */
public final class ServidorHttpSimulado implements AutoCloseable {

    /** Petición recibida: método, ruta, encabezados y cuerpo como texto. */
    public record Peticion(String metodo, String ruta, Map<String, List<String>> encabezados, String cuerpo) {
    }

    private record Respuesta(int estado, String cuerpo) {
    }

    private final HttpServer servidor;
    private final Deque<Respuesta> respuestas = new ArrayDeque<>();
    private final List<Peticion> peticiones = new CopyOnWriteArrayList<>();

    public ServidorHttpSimulado() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        servidor.createContext("/", intercambio -> {
            String cuerpo = new String(intercambio.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            peticiones.add(new Peticion(intercambio.getRequestMethod(), intercambio.getRequestURI().getPath(),
                    intercambio.getRequestHeaders(), cuerpo));
            Respuesta respuesta = siguiente();
            byte[] bytes = respuesta.cuerpo().getBytes(StandardCharsets.UTF_8);
            intercambio.getResponseHeaders().add("Content-Type", "application/json");
            intercambio.sendResponseHeaders(respuesta.estado(), bytes.length == 0 ? -1 : bytes.length);
            try (OutputStream salida = intercambio.getResponseBody()) {
                salida.write(bytes);
            }
        });
        servidor.start();
    }

    /** Encola una respuesta con ese código y ese cuerpo JSON. */
    public ServidorHttpSimulado responder(int estado, String cuerpo) {
        synchronized (respuestas) {
            respuestas.addLast(new Respuesta(estado, cuerpo));
        }
        return this;
    }

    /** Dirección base, por ejemplo http://127.0.0.1:54321 */
    public String url() {
        return "http://127.0.0.1:" + servidor.getAddress().getPort();
    }

    public List<Peticion> peticiones() {
        return List.copyOf(peticiones);
    }

    private Respuesta siguiente() {
        synchronized (respuestas) {
            if (respuestas.isEmpty()) {
                return new Respuesta(500, "{}");
            }
            return respuestas.size() == 1 ? respuestas.peekFirst() : respuestas.pollFirst();
        }
    }

    @Override
    public void close() {
        servidor.stop(0);
    }
}

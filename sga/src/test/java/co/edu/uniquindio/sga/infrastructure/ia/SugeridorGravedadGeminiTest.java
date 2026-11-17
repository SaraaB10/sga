package co.edu.uniquindio.sga.infrastructure.ia;

import co.edu.uniquindio.sga.domain.valueobject.GravedadNovedad;
import co.edu.uniquindio.sga.domain.valueobject.OrigenSugerencia;
import co.edu.uniquindio.sga.domain.valueobject.SugerenciaGravedad;
import co.edu.uniquindio.sga.infrastructure.ServidorHttpSimulado;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Matriz de trazabilidad: 10.2 (sugerencia de gravedad no bloqueante), adaptador de Gemini SIN llamar a Google:
 * valida la respuesta JSON del modelo y, contra un servidor simulado en 127.0.0.1, el reintento único ante 429 o 503
 * y el respaldo con el sugeridor local cuando Gemini falla. Está en el paquete del adaptador para probar
 * interpretar(), que es de paquete.
 */
@ExtendWith(OutputCaptureExtension.class)
class SugeridorGravedadGeminiTest {

    /** Puerto local cerrado: la conexión se rechaza de inmediato, sin salir a internet. */
    private static final String SIN_SERVIDOR = "http://127.0.0.1:9/v1beta";

    private static final String MODELO = "gemini-3.8-flash";

    /** Pausa corta para que las pruebas no esperen el segundo real. */
    private static final Duration PAUSA = Duration.ofMillis(50);

    /** Respuesta de generateContent con la sugerencia MEDIA en el texto del modelo. */
    private static final String RESPUESTA_MEDIA = """
            {"candidates":[{"content":{"parts":[{"text":"{\\"gravedad\\":\\"MEDIA\\",\\"explicacion\\":\\"La nevera no enfría\\"}"}]}}]}
            """;

    private static final String DESCRIPCION = "Huele a gas en la cocina";

    private final SugeridorGravedadGemini gemini = nuevo(SIN_SERVIDOR, "clave-de-prueba");

    @Test
    @DisplayName("10.2: un JSON con gravedad y explicación se acepta como sugerencia de IA, sin importar mayúsculas ni el bloque ```json")
    void interpretaRespuestaValida() {
        assertEquals(Optional.of(new SugerenciaGravedad(GravedadNovedad.ALTA, "Posible fuga de gas", OrigenSugerencia.IA)),
                gemini.interpretar("{\"gravedad\": \"ALTA\", \"explicacion\": \"Posible fuga de gas\"}"));
        assertEquals(GravedadNovedad.MEDIA,
                gemini.interpretar("```json\n{\"gravedad\":\"media\",\"explicacion\":\"Electrodoméstico dañado\"}\n```")
                        .orElseThrow().gravedad());
    }

    @Test
    @DisplayName("10.2: una gravedad desconocida, sin explicación, texto suelto o vacío se descarta")
    void descartaRespuestasInvalidas() {
        assertTrue(gemini.interpretar("{\"gravedad\": \"URGENTE\", \"explicacion\": \"x\"}").isEmpty());
        assertTrue(gemini.interpretar("{\"gravedad\": \"ALTA\", \"explicacion\": \"  \"}").isEmpty());
        assertTrue(gemini.interpretar("{\"gravedad\": \"ALTA\"}").isEmpty());
        assertTrue(gemini.interpretar("La gravedad es ALTA").isEmpty());
        assertTrue(gemini.interpretar("").isEmpty());
        assertTrue(gemini.interpretar(null).isEmpty());
    }

    @Test
    @DisplayName("10.2: una explicación larga se recorta a una explicación corta")
    void recortaExplicacionLarga() {
        String larga = "a".repeat(500);
        SugerenciaGravedad sugerencia = gemini.interpretar(
                "{\"gravedad\": \"BAJA\", \"explicacion\": \"" + larga + "\"}").orElseThrow();

        assertEquals(SugerenciaGravedad.LARGO_MAXIMO_EXPLICACION, sugerencia.explicacion().length());
    }

    @Test
    @DisplayName("Gemini responde bien: la sugerencia es de IA y se pide al modelo configurado")
    void respuestaDeGemini() throws IOException {
        try (ServidorHttpSimulado servidor = new ServidorHttpSimulado().responder(200, RESPUESTA_MEDIA)) {
            SugerenciaGravedad sugerencia = nuevo(servidor.url() + "/v1beta", "clave").sugerir(DESCRIPCION).orElseThrow();

            assertEquals(new SugerenciaGravedad(GravedadNovedad.MEDIA, "La nevera no enfría", OrigenSugerencia.IA),
                    sugerencia);
            assertEquals(1, servidor.peticiones().size());
            assertEquals("/v1beta/models/" + MODELO + ":generateContent", servidor.peticiones().getFirst().ruta());
        }
    }

    @Test
    @DisplayName("Un 503 se reintenta una sola vez: si el reintento responde, la sugerencia es de IA")
    void reintentaUnaVezTras503() throws IOException {
        try (ServidorHttpSimulado servidor = new ServidorHttpSimulado()
                .responder(503, "{\"error\":{\"status\":\"UNAVAILABLE\"}}")
                .responder(200, RESPUESTA_MEDIA)) {
            long inicio = System.nanoTime();
            SugerenciaGravedad sugerencia = nuevo(servidor.url(), "clave").sugerir(DESCRIPCION).orElseThrow();

            assertEquals(OrigenSugerencia.IA, sugerencia.origen());
            assertEquals(2, servidor.peticiones().size());
            assertTrue(Duration.ofNanos(System.nanoTime() - inicio).compareTo(PAUSA) >= 0, "Espera la pausa");
        }
    }

    @Test
    @DisplayName("Un 429 también se reintenta una sola vez")
    void reintentaUnaVezTras429() throws IOException {
        try (ServidorHttpSimulado servidor = new ServidorHttpSimulado()
                .responder(429, "{\"error\":{\"status\":\"RESOURCE_EXHAUSTED\"}}")
                .responder(200, RESPUESTA_MEDIA)) {
            assertEquals(OrigenSugerencia.IA, nuevo(servidor.url(), "clave").sugerir(DESCRIPCION).orElseThrow().origen());
            assertEquals(2, servidor.peticiones().size());
        }
    }

    @Test
    @DisplayName("Si el reintento vuelve a dar 503, responde la sugerencia local y el motivo queda en el log")
    void respaldoLocalSiElReintentoFalla(CapturedOutput salida) throws IOException {
        try (ServidorHttpSimulado servidor = new ServidorHttpSimulado().responder(503, "{}")) {
            SugerenciaGravedad sugerencia = nuevo(servidor.url(), "clave").sugerir(DESCRIPCION).orElseThrow();

            assertEquals(OrigenSugerencia.LOCAL, sugerencia.origen());
            assertEquals(GravedadNovedad.ALTA, sugerencia.gravedad());
            assertEquals(2, servidor.peticiones().size(), "Un intento y un solo reintento");
            assertTrue(salida.getAll().contains("se usa la sugerencia local porque Gemini respondió 503"), salida.getAll());
        }
    }

    @Test
    @DisplayName("Otro error de Gemini (400) no se reintenta: responde la sugerencia local")
    void otroErrorNoSeReintenta() throws IOException {
        try (ServidorHttpSimulado servidor = new ServidorHttpSimulado().responder(400, "{}")) {
            assertEquals(OrigenSugerencia.LOCAL, nuevo(servidor.url(), "clave").sugerir(DESCRIPCION).orElseThrow().origen());
            assertEquals(1, servidor.peticiones().size());
        }
    }

    @Test
    @DisplayName("Una respuesta de Gemini que no es una sugerencia válida usa la sugerencia local")
    void respuestaInvalidaUsaRespaldo() throws IOException {
        try (ServidorHttpSimulado servidor = new ServidorHttpSimulado()
                .responder(200, "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"No sé\"}]}}]}")) {
            assertEquals(OrigenSugerencia.LOCAL, nuevo(servidor.url(), "clave").sugerir(DESCRIPCION).orElseThrow().origen());
        }
    }

    @Test
    @DisplayName("10.2: sin clave no llama a Gemini y responde la sugerencia local")
    void sinClaveUsaRespaldo() throws IOException {
        try (ServidorHttpSimulado servidor = new ServidorHttpSimulado()) {
            assertEquals(OrigenSugerencia.LOCAL, nuevo(servidor.url(), "").sugerir(DESCRIPCION).orElseThrow().origen());
            assertTrue(servidor.peticiones().isEmpty());
        }
    }

    @Test
    @DisplayName("10.2: si Gemini no responde, responde la sugerencia local y no se lanza ningún error")
    void sinServidorUsaRespaldo() {
        assertEquals(OrigenSugerencia.LOCAL, gemini.sugerir(DESCRIPCION).orElseThrow().origen());
    }

    @Test
    @DisplayName("Una descripción vacía no consulta a nadie")
    void descripcionVacia() {
        assertTrue(gemini.sugerir("  ").isEmpty());
    }

    private static SugeridorGravedadGemini nuevo(String url, String clave) {
        return new SugeridorGravedadGemini(JsonMapper.builder().build(), url, MODELO, clave, Duration.ofSeconds(2),
                new SugeridorGravedadLocal(), PAUSA);
    }
}

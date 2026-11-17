package co.edu.uniquindio.sga.infraestructure.config;

import co.edu.uniquindio.sga.domain.port.CredencialesCanal;
import co.edu.uniquindio.sga.domain.repository.CanalVentaRepository;
import co.edu.uniquindio.sga.domain.service.CanalVentaService;
import co.edu.uniquindio.sga.infrastructure.config.DatosInicialesCanalesConfig;
import co.edu.uniquindio.sga.infrastructure.persistence.inmemory.CanalVentaRepositoryInMemory;
import co.edu.uniquindio.sga.infrastructure.persistence.inmemory.EventoCanalRepositoryInMemory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.time.Clock;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Sin sga.canales.credencial-demo, el canal de los datos iniciales se crea igual y su credencial no sale en el log. */
@ExtendWith(OutputCaptureExtension.class)
class CredencialCanalDemoLogTest {

    private static final String GENERADA = "credencial-generada-que-no-debe-verse";

    /** Genera siempre la misma credencial, para buscarla en el log; el "hash" solo la marca. */
    private static final CredencialesCanal CREDENCIALES = new CredencialesCanal() {
        @Override
        public String generar() {
            return GENERADA;
        }

        @Override
        public String cifrar(String credencial) {
            return "hash";
        }

        @Override
        public boolean coincide(String credencial, String hash) {
            return false;
        }
    };

    @Test
    @DisplayName("Sin la credencial configurada, avisa que falta y cómo definirla, sin escribir la generada")
    void noRegistraLaCredencialGenerada(CapturedOutput salida) throws Exception {
        CanalVentaRepository canales = new CanalVentaRepositoryInMemory();
        CanalVentaService servicio = new CanalVentaService(canales, new EventoCanalRepositoryInMemory(), CREDENCIALES);

        new DatosInicialesCanalesConfig()
                .cargarCanalesDeVenta(servicio, canales, CREDENCIALES, "", Clock.systemUTC())
                .run();

        assertTrue(canales.existePorNombre("booking"), "El canal se crea igual");
        assertFalse(salida.getAll().contains(GENERADA), "La credencial generada no debe salir en el log");
        assertTrue(salida.getAll().contains("Falta sga.canales.credencial-demo"));
        assertTrue(salida.getAll().contains("SGA_CANALES_CREDENCIAL_DEMO"));
    }
}

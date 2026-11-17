package co.edu.uniquindio.sga.infrastructure.config;

import co.edu.uniquindio.sga.domain.port.CredencialesCanal;
import co.edu.uniquindio.sga.domain.repository.CanalVentaRepository;
import co.edu.uniquindio.sga.domain.service.CanalVentaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * Datos de prueba de los canales de venta (12.3, 10.3.1): el canal externo "booking", el mismo que envió la
 * reserva externa de los datos iniciales (BK-DEMO-000123). No hace nada si el canal ya existe.
 *
 * Su credencial sale de sga.canales.credencial-demo, que va solo en application-secrets.properties o en la variable
 * de entorno SGA_CANALES_CREDENCIAL_DEMO. Si falta, se genera una al azar que NO se escribe en el log: el log solo
 * avisa que falta y cómo definirla. El administrador puede emitir otra desde el panel de canales.
 */
@Configuration
public class DatosInicialesCanalesConfig {

    private static final Logger log = LoggerFactory.getLogger(DatosInicialesCanalesConfig.class);

    /** Canal de la reserva externa de DatosInicialesReservasConfig. */
    static final String CANAL_DEMO = "booking";

    @Bean
    @Order(3)
    public CommandLineRunner cargarCanalesDeVenta(CanalVentaService canalVentaService,
                                                  CanalVentaRepository canalVentaRepository,
                                                  CredencialesCanal credenciales,
                                                  @Value("${sga.canales.credencial-demo:}") String credencialDemo,
                                                  Clock reloj) {
        return args -> {
            if (canalVentaRepository.existePorNombre(CANAL_DEMO)) {
                return;
            }
            boolean configurada = credencialDemo != null && !credencialDemo.isBlank();
            String credencial = configurada ? credencialDemo.trim() : credenciales.generar();
            canalVentaService.registrar(CANAL_DEMO, credencial, LocalDateTime.now(reloj));
            if (configurada) {
                log.info("Canal externo '{}' creado con la credencial de sga.canales.credencial-demo", CANAL_DEMO);
            } else {
                // La credencial generada nunca se escribe en el log: solo se avisa que falta y cómo definirla
                log.warn("Falta sga.canales.credencial-demo: el canal externo '{}' se creó con una credencial "
                        + "aleatoria que no se muestra. Defínala en application-secrets.properties "
                        + "(sga.canales.credencial-demo=...) o, en producción, con la variable de entorno "
                        + "SGA_CANALES_CREDENCIAL_DEMO, y arranque con la base vacía; o emita una nueva desde "
                        + "Administración → Canales de venta (Regenerar credencial).", CANAL_DEMO);
            }
        };
    }
}

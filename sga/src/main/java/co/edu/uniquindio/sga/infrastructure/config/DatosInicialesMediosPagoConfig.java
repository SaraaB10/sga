package co.edu.uniquindio.sga.infrastructure.config;

import co.edu.uniquindio.sga.domain.entity.MedioPagoAceptado;
import co.edu.uniquindio.sga.domain.repository.MedioPagoRepository;
import co.edu.uniquindio.sga.domain.valueobject.MedioPago;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

/**
 * Medios de pago iniciales del catálogo (L-18), de sga.medios-pago.iniciales ("CODIGO:Nombre" separados por punto y
 * coma). Revisa su propia tabla: si ya tiene medios no hace nada, aunque el resto de los datos ya existiera. Desde
 * ahí el administrador los gestiona en /panel/alojamiento. Corre con @Order(0), antes que las reservas de prueba,
 * que registran pagos con estos medios.
 */
@Configuration
public class DatosInicialesMediosPagoConfig {

    private static final Logger log = LoggerFactory.getLogger(DatosInicialesMediosPagoConfig.class);

    @Bean
    @Order(0)
    public CommandLineRunner cargarMediosPago(MedioPagoRepository medioPagoRepository,
                                              @Value("${sga.medios-pago.iniciales}") String iniciales) {
        return args -> {
            if (!medioPagoRepository.listarTodos().isEmpty()) {
                return;
            }
            for (String par : iniciales.split(";")) {
                String[] partes = par.split(":", 2);
                if (partes.length == 2 && !partes[0].isBlank() && !partes[1].isBlank()) {
                    medioPagoRepository.guardar(MedioPagoAceptado.crear(new MedioPago(partes[0]), partes[1]));
                }
            }
            log.info("Medios de pago iniciales cargados: {}", medioPagoRepository.listarTodos().size());
        };
    }
}

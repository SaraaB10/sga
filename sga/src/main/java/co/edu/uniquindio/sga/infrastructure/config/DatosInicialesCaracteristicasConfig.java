package co.edu.uniquindio.sga.infrastructure.config;

import co.edu.uniquindio.sga.domain.entity.Apartamento;
import co.edu.uniquindio.sga.domain.repository.ApartamentoRepository;
import co.edu.uniquindio.sga.domain.valueobject.Caracteristicas;
import co.edu.uniquindio.sga.domain.valueobject.Dotacion;
import co.edu.uniquindio.sga.domain.valueobject.IdentificacionApartamento;
import co.edu.uniquindio.sga.infrastructure.persistence.jpa.ApartamentoJpaDataRepository;
import co.edu.uniquindio.sga.infrastructure.persistence.jpa.entity.ApartamentoEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;

/**
 * Completa las características de los apartamentos en una base creada antes de que existiera ese campo. Revisa su
 * propio dato (la columna caracteristicas_definidas en null), no si el alojamiento ya existe:
 * <ul>
 *   <li>Los apartamentos de los datos iniciales reciben sus características (DatosInicialesCoralConfig.CARACTERISTICAS).
 *       Si su dotación sigue siendo la de los datos iniciales anteriores, que mezclaba rasgos como "Vista frontal al
 *       mar Caribe", se reemplaza por la dotación actual; si el administrador ya la cambió, se conserva.</li>
 *   <li>Los demás apartamentos quedan sin características: el administrador las define en el panel.</li>
 * </ul>
 * Corre con @Order(1) e invoca antes el cargador del alojamiento (idempotente).
 */
@Configuration
public class DatosInicialesCaracteristicasConfig {

    private static final Logger log = LoggerFactory.getLogger(DatosInicialesCaracteristicasConfig.class);

    /** Dotación de los datos iniciales anteriores, cuando los rasgos del apartamento iban dentro de ella */
    private static final Map<String, List<String>> DOTACION_ANTERIOR = Map.of(
            "APT-101", List.of("Cama extragrande con lencería de algodón egipcio",
                    "Baño privado en mármol con doble ducha", "Cocina integral abierta",
                    "Balcón con tina de hidromasaje privada", "Vista frontal al mar Caribe"),
            "APT-102", List.of("Cama extragrande", "Baño de lujo", "Cocina equipada con barra de cuarzo",
                    "Cava refrigerada de vinos", "Terraza privada con cama balinesa y vista a la bahía"),
            "APT-201", List.of("Dormitorio principal con cama extragrande",
                    "Dormitorio auxiliar con dos camas semidobles", "2 baños completos", "Salón comedor",
                    "Cocina completa", "Terraza con hidromasaje exterior"),
            "APT-202", List.of("Dormitorio principal extragrande", "Dormitorio con dos camas individuales",
                    "2 baños privados", "Cocina integral", "Comedor en balcón panorámico",
                    "Hamaca artesanal de San Jacinto"),
            "APT-301", List.of("Dormitorio principal con vestier y baño privado", "2 dormitorios dobles",
                    "3 baños completos", "Sala", "Cocina amplia",
                    "Terraza privada con piscina de inmersión y vista a las murallas y al mar"));

    @Bean
    @Order(1)
    public CommandLineRunner cargarCaracteristicasApartamentos(
            @Qualifier("cargarDatosCoral") CommandLineRunner cargarDatosCoral,
            ApartamentoJpaDataRepository apartamentosJpa,
            ApartamentoRepository apartamentoRepository,
            TransactionTemplate transaccion) {
        return args -> {
            cargarDatosCoral.run(args);
            List<String> pendientes = transaccion.execute(estado -> apartamentosJpa
                    .findByCaracteristicasDefinidasIsNull().stream()
                    .map(ApartamentoEntity::getIdentificacion)
                    .toList());
            if (pendientes == null || pendientes.isEmpty()) {
                return;
            }
            for (String id : pendientes) {
                completar(apartamentoRepository, id);
            }
            log.info("Características completadas en {} apartamentos de una base existente", pendientes.size());
        };
    }

    /** Pone las características (o ninguna) y, si la dotación es la anterior de los datos iniciales, la actualiza */
    static void completar(ApartamentoRepository apartamentoRepository, String id) {
        Apartamento apartamento = apartamentoRepository.buscarPorIdentificacion(new IdentificacionApartamento(id))
                .orElse(null);
        if (apartamento == null) {
            return;
        }
        List<String> caracteristicas = DatosInicialesCoralConfig.CARACTERISTICAS.getOrDefault(id, List.of());
        apartamento.definirCaracteristicas(new Caracteristicas(caracteristicas));
        List<String> anterior = DOTACION_ANTERIOR.get(id);
        if (anterior != null && anterior.equals(apartamento.getDotacion().elementos())) {
            apartamento.actualizarDatos(apartamento.getNombre(), apartamento.getDescripcion(),
                    apartamento.getDormitorios(), new Dotacion(DatosInicialesCoralConfig.DOTACION.get(id)));
        }
        apartamentoRepository.guardar(apartamento);
    }
}

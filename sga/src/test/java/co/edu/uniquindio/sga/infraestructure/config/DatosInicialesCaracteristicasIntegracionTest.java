package co.edu.uniquindio.sga.infraestructure.config;

import co.edu.uniquindio.sga.domain.entity.Apartamento;
import co.edu.uniquindio.sga.domain.repository.ApartamentoRepository;
import co.edu.uniquindio.sga.domain.valueobject.Caracteristicas;
import co.edu.uniquindio.sga.domain.valueobject.IdentificacionApartamento;
import co.edu.uniquindio.sga.infrastructure.persistence.jpa.ApartamentoJpaDataRepository;
import co.edu.uniquindio.sga.infrastructure.persistence.jpa.entity.ApartamentoEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DatosInicialesCaracteristicasConfig completa las características en una base creada antes de ese campo: los
 * apartamentos de los datos iniciales las reciben, su dotación anterior (que mezclaba rasgos) se actualiza, y la
 * dotación que el administrador ya cambió se conserva.
 */
@Import(SeguridadPruebasConfig.class)
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:sga-caracteristicas")
@DirtiesContext
class DatosInicialesCaracteristicasIntegracionTest {

    @Autowired
    private ApartamentoJpaDataRepository apartamentosJpa;

    @Autowired
    private ApartamentoRepository apartamentoRepository;

    @Autowired
    private TransactionTemplate transaccion;

    @Autowired
    @Qualifier("cargarCaracteristicasApartamentos")
    private CommandLineRunner cargador;

    @Test
    @DisplayName("Una base sin características las recibe al arrancar, sin perder la dotación cambiada")
    void completaUnaBaseExistente() throws Exception {
        // Así quedaba la base antes: sin características y con la dotación que mezclaba rasgos
        comoBaseAnterior("APT-101", List.of("Cama extragrande con lencería de algodón egipcio",
                "Baño privado en mármol con doble ducha", "Cocina integral abierta",
                "Balcón con tina de hidromasaje privada", "Vista frontal al mar Caribe"));
        comoBaseAnterior("APT-102", List.of("Cama extragrande", "Minibar"));        // la cambió el administrador

        cargador.run();

        Apartamento apt101 = apartamento("APT-101");
        assertEquals(List.of("Vista al mar", "Frente a la playa", "Balcón"), apt101.getCaracteristicas().elementos());
        assertEquals(List.of("Cama extragrande con lencería de algodón egipcio",
                "Baño privado en mármol con doble ducha", "Cocina integral abierta", "Tina de hidromasaje privada"),
                apt101.getDotacion().elementos());
        Apartamento apt102 = apartamento("APT-102");
        assertEquals(List.of("Vista a la bahía", "Terraza"), apt102.getCaracteristicas().elementos());
        assertEquals(List.of("Cama extragrande", "Minibar"), apt102.getDotacion().elementos());
        assertTrue(apartamentosJpa.findByCaracteristicasDefinidasIsNull().isEmpty());

        // Idempotente: lo que el administrador cambie después no se vuelve a tocar
        apt101.definirCaracteristicas(null);
        apartamentoRepository.guardar(apt101);
        cargador.run();
        assertTrue(apartamento("APT-101").getCaracteristicas().elementos().isEmpty());
    }

    @Test
    @DisplayName("Una base con \"Admite mascotas\" de los datos anteriores se corrige; si el administrador las cambió, no")
    void corrigeLasCaracteristicasConMascotas() throws Exception {
        conCaracteristicas("APT-301", List.of("Vista al mar", "Vista a las murallas", "Terraza", "Piso alto",
                "Admite mascotas"));
        conCaracteristicas("APT-201", List.of("Terraza", "Admite mascotas", "Piso alto"));     // la cambió el administrador

        cargador.run();

        assertEquals(List.of("Vista al mar", "Vista a las murallas", "Terraza", "Piso alto"),
                apartamento("APT-301").getCaracteristicas().elementos());
        assertEquals(List.of("Terraza", "Admite mascotas", "Piso alto"),
                apartamento("APT-201").getCaracteristicas().elementos());
    }

    private void conCaracteristicas(String id, List<String> caracteristicas) {
        Apartamento a = apartamento(id);
        a.definirCaracteristicas(new Caracteristicas(caracteristicas));
        apartamentoRepository.guardar(a);
    }

    private void comoBaseAnterior(String id, List<String> dotacion) {
        transaccion.executeWithoutResult(estado -> {
            ApartamentoEntity entidad = apartamentosJpa.findByIdentificacion(id).orElseThrow();
            entidad.setCaracteristicas(new ArrayList<>());
            entidad.setCaracteristicasDefinidas(null);
            entidad.setDotacion(new ArrayList<>(dotacion));
            apartamentosJpa.save(entidad);
        });
    }

    private Apartamento apartamento(String id) {
        return apartamentoRepository.buscarPorIdentificacion(new IdentificacionApartamento(id)).orElseThrow();
    }
}

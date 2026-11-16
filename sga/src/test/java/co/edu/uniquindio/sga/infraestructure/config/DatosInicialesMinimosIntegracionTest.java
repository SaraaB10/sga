package co.edu.uniquindio.sga.infraestructure.config;

import co.edu.uniquindio.sga.domain.entity.Alojamiento;
import co.edu.uniquindio.sga.domain.entity.Apartamento;
import co.edu.uniquindio.sga.domain.entity.Bloqueo;
import co.edu.uniquindio.sga.domain.entity.Reserva;
import co.edu.uniquindio.sga.domain.repository.AlojamientoRepository;
import co.edu.uniquindio.sga.domain.repository.ApartamentoRepository;
import co.edu.uniquindio.sga.domain.repository.BloqueoRepository;
import co.edu.uniquindio.sga.domain.repository.ReservaRepository;
import co.edu.uniquindio.sga.domain.valueobject.CanalOrigen;
import co.edu.uniquindio.sga.domain.valueobject.EstadoOperativo;
import co.edu.uniquindio.sga.domain.valueobject.EstadoReserva;
import co.edu.uniquindio.sga.domain.valueobject.Estancia;
import co.edu.uniquindio.sga.domain.valueobject.IdentificacionApartamento;
import co.edu.uniquindio.sga.infrastructure.config.setup.DefaultUserInitializer;
import co.edu.uniquindio.sga.infrastructure.persistence.jpa.UsuarioJpaDataRepository;
import co.edu.uniquindio.sga.infrastructure.security.RolUsuario;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Datos de prueba mínimos (12.3) con la base recién cargada: los 5 apartamentos con tarifa en todas las temporadas,
 * al menos un usuario por rol, reservas en los 6 estados y en los 3 canales, al menos un bloqueo vigente y al menos
 * un apartamento FUERA_DE_SERVICIO con su bloqueo y sin reservas activas que lo contradigan. Volver a correr los
 * cargadores no duplica nada, y si a una base existente le falta algo, lo completan.
 */
@Import(SeguridadPruebasConfig.class)
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:sga-datos-minimos")
@DirtiesContext
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DatosInicialesMinimosIntegracionTest {

    private static final List<String> INVENTARIO = List.of("APT-101", "APT-102", "APT-201", "APT-202", "APT-301");

    @Autowired
    private AlojamientoRepository alojamientoRepository;

    @Autowired
    private ApartamentoRepository apartamentoRepository;

    @Autowired
    private BloqueoRepository bloqueoRepository;

    @Autowired
    private ReservaRepository reservaRepository;

    @Autowired
    private UsuarioJpaDataRepository usuarioRepository;

    @Autowired
    private DefaultUserInitializer usuariosIniciales;

    @Autowired
    @Qualifier("cargarDatosCoral")
    private CommandLineRunner cargarDatosCoral;

    @Autowired
    @Qualifier("cargarReservasDePrueba")
    private CommandLineRunner cargarReservas;

    @Autowired
    private Clock reloj;

    @Test
    @Order(1)
    @DisplayName("Todos los apartamentos existen, activos y con tarifa en todas las temporadas")
    void apartamentosConTarifasCompletas() {
        Alojamiento coral = alojamientoRepository.obtener().orElseThrow();
        for (String id : INVENTARIO) {
            Apartamento apartamento = apartamento(id);
            assertTrue(apartamento.isActivo(), id + " no está activo");
            assertTrue(apartamento.tieneTarifasCompletas(coral.getTemporadas()),
                    id + " no tiene tarifa en todas las temporadas");
        }
    }

    @Test
    @Order(2)
    @DisplayName("Hay al menos un usuario por rol")
    void unUsuarioPorRol() {
        for (RolUsuario rol : RolUsuario.values()) {
            assertTrue(usuarioRepository.existsByRol(rol), "No hay usuarios con rol " + rol);
        }
    }

    @Test
    @Order(3)
    @DisplayName("Hay reservas en los 6 estados y en los 3 canales")
    void reservasEnTodosLosEstadosYCanales() {
        List<Reserva> reservas = reservaRepository.listarTodas();
        Set<EstadoReserva> estados = reservas.stream().map(Reserva::getEstado).collect(Collectors.toSet());
        Set<CanalOrigen> canales = reservas.stream().map(Reserva::getCanalOrigen).collect(Collectors.toSet());
        assertEquals(Set.of(EstadoReserva.values()), estados);
        assertEquals(Set.of(CanalOrigen.values()), canales);
    }

    @Test
    @Order(4)
    @DisplayName("Hay al menos un bloqueo vigente, y ninguno cae sobre noches de reservas activas (RN-01)")
    void bloqueoVigenteSinChoques() {
        List<Bloqueo> vigentes = bloqueosVigentes();
        assertFalse(vigentes.isEmpty(), "No hay bloqueos vigentes");
        for (Bloqueo bloqueo : vigentes) {
            assertTrue(reservaRepository.listarActivasPorApartamento(bloqueo.getApartamento()).stream()
                            .noneMatch(r -> bloqueo.seSolapaCon(r.getEstancia())),
                    "El bloqueo de " + bloqueo.getApartamento().codigo() + " choca con una reserva activa");
        }
    }

    @Test
    @Order(5)
    @DisplayName("Hay un apartamento FUERA_DE_SERVICIO, bloqueado hoy y sin reservas activas en esas noches")
    void apartamentoFueraDeServicio() {
        LocalDate hoy = LocalDate.now(reloj);
        List<Apartamento> fueraDeServicio = apartamentoRepository.listarNoEliminados().stream()
                .filter(a -> a.getEstadoOperativo() == EstadoOperativo.FUERA_DE_SERVICIO)
                .toList();
        assertFalse(fueraDeServicio.isEmpty(), "Ningún apartamento está fuera de servicio");
        for (Apartamento apartamento : fueraDeServicio) {
            List<Bloqueo> bloqueos = bloqueoRepository.listarVigentesPorApartamento(apartamento.getIdentificacion());
            Bloqueo deHoy = bloqueos.stream().filter(b -> b.afectaFecha(hoy)).findFirst().orElseThrow(
                    () -> new AssertionError(apartamento.getIdentificacion().codigo() + " no está bloqueado hoy"));
            Estancia noches = new Estancia(deHoy.getRango().fechaInicio(), deHoy.getRango().fechaFin());
            assertTrue(reservaRepository.listarActivasQueSeSolapan(apartamento.getIdentificacion(), noches).isEmpty(),
                    apartamento.getIdentificacion().codigo() + " tiene reservas activas mientras está fuera de servicio");
        }
    }

    @Test
    @Order(6)
    @DisplayName("Volver a correr los cargadores sobre la base ya cargada no duplica nada")
    void volverACargarNoDuplica() throws Exception {
        long usuarios = usuarioRepository.count();
        int apartamentos = apartamentoRepository.listarNoEliminados().size();
        int bloqueos = bloqueosVigentes().size();
        int reservas = reservaRepository.listarTodas().size();

        usuariosIniciales.run();
        cargarDatosCoral.run();
        cargarReservas.run();

        assertEquals(usuarios, usuarioRepository.count());
        assertEquals(apartamentos, apartamentoRepository.listarNoEliminados().size());
        assertEquals(bloqueos, bloqueosVigentes().size());
        assertEquals(reservas, reservaRepository.listarTodas().size());
    }

    @Test
    @Order(7)
    @DisplayName("Si a una base existente le falta el apartamento fuera de servicio, el cargador lo completa")
    void completaElFueraDeServicio() throws Exception {
        apartamentoRepository.listarNoEliminados().stream()
                .filter(a -> a.getEstadoOperativo() == EstadoOperativo.FUERA_DE_SERVICIO)
                .forEach(a -> {
                    a.restablecerServicio();
                    apartamentoRepository.guardar(a);
                });
        int bloqueos = bloqueosVigentes().size();

        cargarDatosCoral.run();

        assertEquals(EstadoOperativo.FUERA_DE_SERVICIO, apartamento("APT-202").getEstadoOperativo());
        assertEquals(bloqueos, bloqueosVigentes().size(), "Ya tenía su bloqueo de hoy: no debe registrar otro");
    }

    private List<Bloqueo> bloqueosVigentes() {
        LocalDate hoy = LocalDate.now(reloj);
        return apartamentoRepository.listarNoEliminados().stream()
                .flatMap(a -> bloqueoRepository.listarVigentesPorApartamento(a.getIdentificacion()).stream())
                .filter(b -> b.getRango().fechaFin().isAfter(hoy))
                .toList();
    }

    private Apartamento apartamento(String id) {
        return apartamentoRepository.buscarPorIdentificacion(new IdentificacionApartamento(id)).orElseThrow();
    }
}

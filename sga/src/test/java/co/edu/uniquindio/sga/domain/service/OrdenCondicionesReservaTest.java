package co.edu.uniquindio.sga.domain.service;

import co.edu.uniquindio.sga.domain.entity.Apartamento;
import co.edu.uniquindio.sga.domain.entity.Bloqueo;
import co.edu.uniquindio.sga.domain.entity.Reserva;
import co.edu.uniquindio.sga.domain.entity.Temporada;
import co.edu.uniquindio.sga.domain.exception.ReglaDominioException;
import co.edu.uniquindio.sga.domain.service.ModificacionReservaService.DatosModificacion;
import co.edu.uniquindio.sga.domain.valueobject.CanalOrigen;
import co.edu.uniquindio.sga.domain.valueobject.Capacidad;
import co.edu.uniquindio.sga.domain.valueobject.DepositoGarantia;
import co.edu.uniquindio.sga.domain.valueobject.Dinero;
import co.edu.uniquindio.sga.domain.valueobject.Dotacion;
import co.edu.uniquindio.sga.domain.valueobject.Estancia;
import co.edu.uniquindio.sga.domain.valueobject.IdentificacionApartamento;
import co.edu.uniquindio.sga.domain.valueobject.Imagen;
import co.edu.uniquindio.sga.domain.valueobject.Mascotas;
import co.edu.uniquindio.sga.domain.valueobject.Ocupante;
import co.edu.uniquindio.sga.domain.valueobject.RangoFechas;
import co.edu.uniquindio.sga.domain.valueobject.Tarifa;
import co.edu.uniquindio.sga.domain.valueobject.TipoTemporada;
import co.edu.uniquindio.sga.infrastructure.persistence.inmemory.ServicioAdicionalRepositoryInMemory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Orden de las seis condiciones de creación y de modificación de una reserva (DisponibilidadApartamentoService
 * .validarCondicionesDeReserva): salida posterior a la entrada (RN-03), entrada no anterior a hoy (RN-04),
 * apartamento activo con tarifas completas, capacidad (RN-02), bloqueos y solapamiento (RN-07, RN-01) y tiempo de
 * preparación (RN-20). Cada caso viola dos condiciones a la vez y debe fallar por la que va primero.
 */
class OrdenCondicionesReservaTest {

    private static final IdentificacionApartamento APT_102 = new IdentificacionApartamento("APT-102");

    private final EscenarioCoral escenario = new EscenarioCoral();
    private final CotizadorEstanciaService cotizador =
            new CotizadorEstanciaService(escenario.apartamentos, escenario.alojamientos);
    private final DisponibilidadApartamentoService disponibilidad = new DisponibilidadApartamentoService(
            escenario.reservas, escenario.apartamentos, escenario.bloqueos, escenario.alojamientos, cotizador);
    private final CreacionReservaService creacion = new CreacionReservaService(escenario.reservas, escenario.folios,
            escenario.apartamentos, escenario.alojamientos, new ServicioAdicionalRepositoryInMemory(), disponibilidad,
            cotizador, DepositoGarantia.de("Depósito de garantía", Dinero.de(250_000)), "SRV-RECEPCION-NOCTURNA");
    private final ModificacionReservaService modificacion = new ModificacionReservaService(escenario.reservas,
            escenario.folios, escenario.apartamentos, escenario.alojamientos, disponibilidad, cotizador);

    // Hoy es el 1 de octubre de 2026 (EscenarioCoral.AHORA); APT-101 admite 3 personas.
    private final Estancia pasada = new Estancia(LocalDate.of(2026, 9, 20), LocalDate.of(2026, 9, 22));
    private final Estancia futura = new Estancia(LocalDate.of(2026, 10, 20), LocalDate.of(2026, 10, 22));

    @Test
    @DisplayName("Orden 1 antes que 2: salida no posterior a la entrada en el pasado falla por RN-03")
    void salidaNoPosteriorAntesQueEntradaPasada() {
        ReglaDominioException error = assertThrows(ReglaDominioException.class,
                () -> new Estancia(LocalDate.of(2026, 9, 22), LocalDate.of(2026, 9, 20)));

        assertTrue(error.getMessage().startsWith("RN-03"), error.getMessage());
    }

    @Test
    @DisplayName("Orden 2 antes que 3: entrada en el pasado en un apartamento inactivo falla por RN-04")
    void entradaPasadaAntesQueApartamentoInactivo() {
        registrarApartamentoInactivo();

        assertFallaPor("RN-04", () -> crear(APT_102, pasada, ocupantes(1)));
    }

    @Test
    @DisplayName("Orden 2 antes que 4: entrada en el pasado con más ocupantes que la capacidad falla por RN-04")
    void entradaPasadaAntesQueCapacidad() {
        assertFallaPor("RN-04", () -> crear(EscenarioCoral.APT_101, pasada, ocupantes(4)));
    }

    @Test
    @DisplayName("Orden 2 antes que 5: entrada en el pasado sobre noches bloqueadas falla por RN-04")
    void entradaPasadaAntesQueBloqueo() {
        bloquear(new RangoFechas(LocalDate.of(2026, 9, 19), LocalDate.of(2026, 9, 25)));

        assertFallaPor("RN-04", () -> crear(EscenarioCoral.APT_101, pasada, ocupantes(1)));
    }

    @Test
    @DisplayName("Orden 3 antes que 4: apartamento inactivo con más ocupantes que la capacidad falla por estar inactivo")
    void apartamentoInactivoAntesQueCapacidad() {
        registrarApartamentoInactivo();

        ReglaDominioException error = assertThrows(ReglaDominioException.class,
                () -> crear(APT_102, futura, ocupantes(4)));

        assertTrue(error.getMessage().contains("no está activo"), error.getMessage());
    }

    @Test
    @DisplayName("Orden 3 antes que 4: apartamento sin tarifa en una temporada nueva y grupo excedido falla por la tarifa")
    void tarifasIncompletasAntesQueCapacidad() {
        escenario.alojamiento.agregarTemporada(Temporada.crear("T-MEDIA", "Media", TipoTemporada.MEDIA,
                List.of(new RangoFechas(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 10)))));
        escenario.alojamientos.guardar(escenario.alojamiento);

        ReglaDominioException error = assertThrows(ReglaDominioException.class,
                () -> crear(EscenarioCoral.APT_101, futura, ocupantes(4)));

        assertTrue(error.getMessage().contains("no tiene tarifa en todas las temporadas"), error.getMessage());
    }

    @Test
    @DisplayName("Orden 4 antes que 5: más ocupantes que la capacidad sobre noches ya reservadas falla por RN-02")
    void capacidadAntesQueSolapamiento() {
        crear(EscenarioCoral.APT_101, futura, ocupantes(1));

        assertFallaPor("RN-02", () -> crear(EscenarioCoral.APT_101, futura, ocupantes(4)));
    }

    @Test
    @DisplayName("Orden 4 antes que 5: más ocupantes que la capacidad sobre noches bloqueadas falla por RN-02")
    void capacidadAntesQueBloqueo() {
        bloquear(new RangoFechas(LocalDate.of(2026, 10, 19), LocalDate.of(2026, 10, 25)));

        assertFallaPor("RN-02", () -> crear(EscenarioCoral.APT_101, futura, ocupantes(4)));
    }

    @Test
    @DisplayName("Sin otra violación, cada condición sigue fallando por sí sola (RN-04, RN-02 y RN-01)")
    void cadaCondicionPorSiSola() {
        assertFallaPor("RN-04", () -> crear(EscenarioCoral.APT_101, pasada, ocupantes(1)));
        assertFallaPor("RN-02", () -> crear(EscenarioCoral.APT_101, futura, ocupantes(4)));
        crear(EscenarioCoral.APT_101, futura, ocupantes(1));
        assertFallaPor("RN-01", () -> crear(EscenarioCoral.APT_101, futura, ocupantes(1)));
    }

    @Test
    @DisplayName("RN-14: la modificación repite el orden; fechas en el pasado y grupo excedido falla por RN-04")
    void modificacionRespetaElOrden() {
        Reserva reserva = crear(EscenarioCoral.APT_101, futura, ocupantes(1));

        assertFallaPor("RN-04", () -> modificacion.modificar(reserva.getCodigo(),
                new DatosModificacion(null, pasada, ocupantes(4)), EscenarioCoral.AHORA));
    }

    @Test
    @DisplayName("RN-14: la modificación con grupo excedido sobre noches bloqueadas falla por RN-02")
    void modificacionCapacidadAntesQueBloqueo() {
        Reserva reserva = crear(EscenarioCoral.APT_101, futura, ocupantes(1));
        bloquear(new RangoFechas(LocalDate.of(2026, 10, 25), LocalDate.of(2026, 10, 30)));
        Estancia bloqueada = new Estancia(LocalDate.of(2026, 10, 26), LocalDate.of(2026, 10, 28));

        assertFallaPor("RN-02", () -> modificacion.modificar(reserva.getCodigo(),
                new DatosModificacion(null, bloqueada, ocupantes(4)), EscenarioCoral.AHORA));
        assertEquals(futura, escenario.reservas.buscarPorCodigo(reserva.getCodigo()).orElseThrow().getEstancia());
    }

    // ---------------------------------------------------------------- Apoyo

    private Reserva crear(IdentificacionApartamento apartamento, Estancia estancia, List<Ocupante> ocupantes) {
        return creacion.registrarNuevaReserva(apartamento, estancia, escenario.titular, ocupantes,
                LocalTime.of(16, 0), null, escenario.politica, CanalOrigen.DIRECTO, null, Mascotas.ninguna(),
                EscenarioCoral.AHORA);
    }

    /** El titular más los acompañantes adultos necesarios para llegar al total. */
    private List<Ocupante> ocupantes(int total) {
        List<Ocupante> lista = new java.util.ArrayList<>(List.of(escenario.ana));
        for (int i = 1; i < total; i++) {
            lista.add(new Ocupante("Acompañante " + i, null, LocalDate.of(1985, 1, i)));
        }
        return lista;
    }

    private void registrarApartamentoInactivo() {
        Apartamento inactivo = Apartamento.crear(APT_102, "Apartamento 102", "Vista a la ciudad", 1, new Capacidad(2),
                new Dotacion(List.of("Cocina")), List.of(new Imagen("https://coral.co/apt-102.jpg", true)));
        inactivo.definirTarifa(new Tarifa("T-BASE", Dinero.de(300_000)));
        escenario.apartamentos.guardar(inactivo);
    }

    private void bloquear(RangoFechas rango) {
        escenario.bloqueos.guardar(Bloqueo.registrar("BLQ-1", EscenarioCoral.APT_101, rango, "Mantenimiento"));
    }

    private static void assertFallaPor(String regla, org.junit.jupiter.api.function.Executable accion) {
        ReglaDominioException error = assertThrows(ReglaDominioException.class, accion);
        assertTrue(error.getMessage().startsWith(regla), "Se esperaba " + regla + " y falló por: " + error.getMessage());
    }
}

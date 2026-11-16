package co.edu.uniquindio.sga.domain.service;

import co.edu.uniquindio.sga.domain.entity.Reserva;
import co.edu.uniquindio.sga.domain.exception.GrupoSinOcupanteFacturableException;
import co.edu.uniquindio.sga.domain.service.ModificacionReservaService.DatosModificacion;
import co.edu.uniquindio.sga.domain.valueobject.CanalOrigen;
import co.edu.uniquindio.sga.domain.valueobject.Cotizacion;
import co.edu.uniquindio.sga.domain.valueobject.DepositoGarantia;
import co.edu.uniquindio.sga.domain.valueobject.Dinero;
import co.edu.uniquindio.sga.domain.valueobject.Estancia;
import co.edu.uniquindio.sga.domain.valueobject.Mascotas;
import co.edu.uniquindio.sga.domain.valueobject.Ocupante;
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
 * El grupo debe incluir al menos un ocupante facturable (el titular siempre lo es). Lo exigen la cotización, la
 * búsqueda de disponibles con fechas de nacimiento, la creación y la modificación de reservas.
 * Escenario: umbral de 6 años, hoy 1 de octubre de 2026 y estancia del 20 al 22 de octubre en temporada base.
 */
class GrupoSinOcupanteFacturableTest {

    private final EscenarioCoral escenario = new EscenarioCoral();
    private final CotizadorEstanciaService cotizador =
            new CotizadorEstanciaService(escenario.apartamentos, escenario.alojamientos);
    private final DisponibilidadApartamentoService disponibilidad = new DisponibilidadApartamentoService(
            escenario.reservas, escenario.apartamentos, escenario.bloqueos, escenario.alojamientos, cotizador);
    private final CreacionReservaService creacion = new CreacionReservaService(escenario.reservas, escenario.folios,
            escenario.apartamentos, escenario.alojamientos, new ServicioAdicionalRepositoryInMemory(), disponibilidad,
            cotizador, "SRV-RECEPCION-NOCTURNA");
    private final ModificacionReservaService modificacion = new ModificacionReservaService(escenario.reservas,
            escenario.folios, escenario.apartamentos, escenario.alojamientos, disponibilidad, cotizador);

    private final Estancia estancia = new Estancia(LocalDate.of(2026, 10, 20), LocalDate.of(2026, 10, 22));
    private final Ocupante nina = new Ocupante("Sofía Gómez", null, LocalDate.of(2022, 3, 2));
    private final Ocupante bebe = new Ocupante("Tomás Gómez", null, LocalDate.of(2025, 1, 10));

    @Test
    @DisplayName("Cotización: un grupo solo de menores se rechaza con el mensaje del grupo sin facturables")
    void cotizacionSinFacturables() {
        GrupoSinOcupanteFacturableException error = assertThrows(GrupoSinOcupanteFacturableException.class,
                () -> cotizador.cotizar(escenario.apartamento, estancia, List.of(nina, bebe), null));

        assertEquals("El grupo debe incluir al menos un adulto que pague noche; el titular siempre es facturable",
                error.getMessage());
    }

    @Test
    @DisplayName("Cotización: un adulto y un menor cobran una sola persona por noche")
    void unAdultoYUnMenorCobranUnaPersona() {
        Cotizacion cotizacion = cotizador.cotizar(escenario.apartamento, estancia, List.of(escenario.ana, nina), null);

        assertEquals(Dinero.de(2 * 310_000), cotizacion.valorTotal());
    }

    @Test
    @DisplayName("Búsqueda: fechas de nacimiento solo de menores se rechazan, aunque no haya apartamentos libres")
    void busquedaSinFacturables() {
        List<LocalDate> menores = List.of(nina.fechaNacimiento(), bebe.fechaNacimiento());
        escenario.apartamento.retirarDeLaVenta(false);
        escenario.apartamentos.guardar(escenario.apartamento);

        assertThrows(GrupoSinOcupanteFacturableException.class,
                () -> disponibilidad.buscarDisponibles(estancia, 2, menores, null));
        assertThrows(GrupoSinOcupanteFacturableException.class,
                () -> cotizador.estimar(escenario.apartamento, estancia, 2, menores));
    }

    @Test
    @DisplayName("Búsqueda: sin fechas de nacimiento todos cuentan como facturables y no se rechaza")
    void busquedaSinFechasNoSeRechaza() {
        assertEquals(1, disponibilidad.buscarDisponibles(estancia, 2, null, null).size());
    }

    @Test
    @DisplayName("Creación: una reserva cuyo grupo es solo de menores no se crea")
    void creacionSinFacturables() {
        assertThrows(GrupoSinOcupanteFacturableException.class, () -> crear(List.of(nina, bebe)));

        assertTrue(escenario.reservas.listarTodas().isEmpty());
    }

    @Test
    @DisplayName("Modificación: dejar el grupo solo con menores se rechaza y la reserva no cambia")
    void modificacionSinFacturables() {
        Reserva reserva = crear(List.of(escenario.ana, nina));

        assertThrows(GrupoSinOcupanteFacturableException.class, () -> modificacion.modificar(reserva.getCodigo(),
                new DatosModificacion(null, null, List.of(nina, bebe)), EscenarioCoral.AHORA));
        assertEquals(2, escenario.reservas.buscarPorCodigo(reserva.getCodigo()).orElseThrow().getOcupantes().size());
    }

    private Reserva crear(List<Ocupante> ocupantes) {
        return creacion.registrarNuevaReserva(EscenarioCoral.APT_101, estancia, escenario.titular, ocupantes,
                LocalTime.of(16, 0), null, escenario.politica, CanalOrigen.DIRECTO, null, Mascotas.ninguna(),
                EscenarioCoral.AHORA);
    }
}

package co.edu.uniquindio.sga.domain.service;

import co.edu.uniquindio.sga.domain.exception.ReglaDominioException;
import co.edu.uniquindio.sga.domain.valueobject.Estancia;
import co.edu.uniquindio.sga.domain.valueobject.RangoFechas;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;

import static co.edu.uniquindio.sga.domain.service.EscenarioCoral.APT_101;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** CU-13 (RN-01, RN-07): un bloqueo no puede caer sobre noches de una reserva activa, y el mensaje dice cuál. */
class RegistrarBloqueoServiceTest {

    private final EscenarioCoral escenario = new EscenarioCoral();
    private final RegistrarBloqueoService servicio =
            new RegistrarBloqueoService(escenario.bloqueos, escenario.apartamentos, escenario.reservas);

    @Test
    @DisplayName("RN-01: bloquear sobre una reserva activa responde con el código, el estado y las fechas de esa reserva")
    void elMensajeDiceQueReservaLoImpide() {
        escenario.reservaPendiente(7, new Estancia(LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 13)),
                LocalTime.of(16, 0));

        ReglaDominioException error = assertThrows(ReglaDominioException.class, () -> servicio.registrarBloqueo(APT_101,
                new RangoFechas(LocalDate.of(2026, 11, 12), LocalDate.of(2026, 11, 15)), "Pintura del balcón"));

        assertEquals("RN-01: No se puede bloquear el apartamento APT-101: la reserva RES-2026-00007 (PENDIENTE, del "
                + "2026-11-10 al 2026-11-13) ocupa esas noches", error.getMessage());
        assertEquals(0, escenario.bloqueos.listarVigentesPorApartamento(APT_101).size(), "No debía guardarse el bloqueo");
    }

    @Test
    @DisplayName("Un bloqueo que empieza el día de salida de la reserva no choca (rango semiabierto)")
    void bloqueoDesdeElDiaDeSalida() {
        escenario.reservaPendiente(8, new Estancia(LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 13)),
                LocalTime.of(16, 0));

        assertDoesNotThrow(() -> servicio.registrarBloqueo(APT_101,
                new RangoFechas(LocalDate.of(2026, 11, 13), LocalDate.of(2026, 11, 15)), "Mantenimiento"));
    }
}

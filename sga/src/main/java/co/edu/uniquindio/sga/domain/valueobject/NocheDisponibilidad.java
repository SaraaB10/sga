package co.edu.uniquindio.sga.domain.valueobject;

import co.edu.uniquindio.sga.domain.exception.ReglaDominioException;

import java.time.LocalDate;

/**
 * Disponibilidad de un apartamento en una noche, tal como la ve un visitante: si la noche se puede vender y si la
 * retira de la venta un bloqueo del alojamiento (mantenimiento, reparación). Nunca revela datos de las reservas: una
 * noche ocupada por una reserva o por el tiempo de preparación solo aparece como no libre.
 */
public record NocheDisponibilidad(LocalDate fecha, boolean libre, boolean bloqueada) {

    public NocheDisponibilidad {
        if (fecha == null) {
            throw new ReglaDominioException("La fecha de la noche es obligatoria");
        }
    }
}

package co.edu.uniquindio.sga.domain.valueobject;

import co.edu.uniquindio.sga.domain.exception.ReglaDominioException;

import java.time.LocalDate;

/**
 * Disponibilidad de un apartamento en una noche, tal como la ve un visitante: solo dice si la noche se puede vender.
 * No revela si la ocupa una reserva, un bloqueo o el tiempo de preparación.
 */
public record NocheDisponibilidad(LocalDate fecha, boolean libre) {

    public NocheDisponibilidad {
        if (fecha == null) {
            throw new ReglaDominioException("La fecha de la noche es obligatoria");
        }
    }
}

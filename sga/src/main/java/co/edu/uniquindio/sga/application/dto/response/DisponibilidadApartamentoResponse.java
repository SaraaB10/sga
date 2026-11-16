package co.edu.uniquindio.sga.application.dto.response;

import java.time.LocalDate;
import java.util.List;

/**
 * Calendario público de disponibilidad de un apartamento: una entrada por noche, desde la fecha desde hasta la noche
 * anterior a hasta. Dice si cada noche está libre y si la retira un bloqueo; no incluye datos de las reservas.
 */
public record DisponibilidadApartamentoResponse(
        String apartamento,                   // "APT-201"
        LocalDate desde,
        LocalDate hasta,
        List<Noche> noches
) {

    /**
     * Una noche del calendario: libre es true si se puede reservar; bloqueada es true si un bloqueo del alojamiento
     * (mantenimiento, reparación) la retira de la venta.
     */
    public record Noche(LocalDate fecha, boolean libre, boolean bloqueada) {
    }
}

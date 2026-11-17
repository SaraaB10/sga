package co.edu.uniquindio.sga.application.dto.response;

import java.time.LocalDate;
import java.util.List;

/**
 * Calendario público de disponibilidad de un apartamento: una entrada por noche, desde la fecha desde hasta la noche
 * anterior a hasta. Solo dice si cada noche está libre; no incluye datos de reservas ni de bloqueos.
 */
public record DisponibilidadApartamentoResponse(
        String apartamento,                   // "APT-201"
        LocalDate desde,
        LocalDate hasta,
        List<Noche> noches
) {

    /** Una noche del calendario: libre es true si se puede reservar. */
    public record Noche(LocalDate fecha, boolean libre) {
    }
}

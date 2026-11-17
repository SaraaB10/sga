package co.edu.uniquindio.sga.application.dto.response;

import java.time.LocalDate;

/**
 * Respuesta de CU-13: el bloqueo. fechaFin es la primera noche libre (rango semiabierto, 3.1). fechaLevantamiento
 * es el día en que se levantó, o null si sigue vigente.
 */
public record BloqueoResponse(
        String id,
        String identificacionApartamento,
        LocalDate fechaInicio,
        LocalDate fechaFin,
        String motivo,
        boolean vigente,
        LocalDate fechaLevantamiento
) {
}
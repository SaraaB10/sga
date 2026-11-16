package co.edu.uniquindio.sga.application.dto.response;

import java.time.LocalDate;

/**
 * Un pago registrado en el folio con su medio y su fecha (RN-15). El valor va en pesos enteros y el medio, como su
 * código (el nombre está en GET /api/medios-pago).
 */
public record PagoResponse(
        long valor,
        String medio,
        LocalDate fecha
) {
}

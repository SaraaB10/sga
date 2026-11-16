package co.edu.uniquindio.sga.application.dto.response;

import java.time.LocalDate;

/**
 * Un movimiento de dinero del folio con su medio y su fecha (RN-15). El valor va en pesos enteros y el medio, como su
 * código (el nombre está en GET /api/medios-pago).
 *
 * @param devolucion true si es dinero devuelto al huésped (el depósito de garantía, RP-02): resta del total pagado.
 */
public record PagoResponse(
        long valor,
        String medio,
        LocalDate fecha,
        boolean devolucion
) {
}

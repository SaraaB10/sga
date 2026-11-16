package co.edu.uniquindio.sga.application.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * CU-15. POST /api/reservas/{codigo}/folio/pagos
 * Abono contra el folio con su medio (RN-15). La fecha la pone el SGA con el reloj de Colombia. Un pago no se edita
 * ni se borra: una corrección es un movimiento inverso (RN-16). El medio es el código de uno del catálogo
 * (GET /api/medios-pago); que exista y esté activo lo valida el caso de uso (409).
 */
public record RegistrarPagoRequest(
        @NotNull(message = "El valor del pago es obligatorio")
        @Positive(message = "El valor del pago debe ser mayor que cero")
        Long valor,                              // pesos enteros, nunca double

        @NotBlank(message = "El medio de pago es obligatorio")
        @Size(max = 40, message = "El medio de pago admite máximo 40 caracteres")
        String medio                             // código, por ejemplo TARJETA_DEBITO_CREDITO (L-18)
) {
}

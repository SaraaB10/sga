package co.edu.uniquindio.sga.application.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * RP-02. POST /api/reservas/{codigo}/folio/devolucion-deposito
 * Medio con el que se devuelve el depósito de garantía al huésped. El valor no se envía: es el del depósito (o lo
 * pagado de él). La fecha la pone el SGA. Que el medio exista y esté activo lo valida el caso de uso (409).
 */
public record DevolverDepositoRequest(
        @NotBlank(message = "El medio de la devolución es obligatorio")
        @Size(max = 40, message = "El medio de la devolución admite máximo 40 caracteres")
        String medio
) {
}

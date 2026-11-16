package co.edu.uniquindio.sga.application.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * POST /api/medios-pago
 * Nombre del medio de pago nuevo (por ejemplo "Efectivo"); el código (EFECTIVO) se toma del nombre. Debe tener al
 * menos una letra o un número para formar el código.
 */
public record CrearMedioPagoRequest(
        @NotBlank(message = "El nombre del medio de pago es obligatorio")
        @Size(min = 2, max = 80, message = "El nombre del medio de pago debe tener entre 2 y 80 caracteres")
        @Pattern(regexp = "(?s)^\\s*$|.*[\\p{L}\\p{N}].*[\\p{L}\\p{N}].*",
                message = "El nombre del medio de pago debe tener al menos dos letras o números")
        String nombre
) {
}

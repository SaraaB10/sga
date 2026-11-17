package co.edu.uniquindio.sga.application.dto.request;
import co.edu.uniquindio.sga.application.dto.validation.FechaNacimientoRazonable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Ocupante de la reserva (3.2, 7.2): el nombre y la fecha de nacimiento son obligatorios; el documento es opcional.
 * La edad nunca viaja ni se guarda: el dominio la calcula con la fecha de nacimiento (RN-06, umbral de 6 años en Coral).
 */
public record OcupanteRequest(
        @NotBlank(message = "El nombre del ocupante es obligatorio")
        @Pattern(regexp = ReglaNombre.PATRON, message = ReglaNombre.MENSAJE)
        String nombre,

        @Size(max = 20, message = "El tipo de documento admite máximo 20 caracteres")
        String tipoDocumento,      // opcional, junto con numeroDocumento
        @Pattern(regexp = ReglaDocumento.PATRON, message = ReglaDocumento.MENSAJE)
        String numeroDocumento,    // opcional

        @NotNull(message = "La fecha de nacimiento del ocupante es obligatoria")
        @PastOrPresent(message = "La fecha de nacimiento no puede ser futura")
        @FechaNacimientoRazonable
        LocalDate fechaNacimiento
) {
}

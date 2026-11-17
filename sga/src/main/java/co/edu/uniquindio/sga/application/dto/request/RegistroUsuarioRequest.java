package co.edu.uniquindio.sga.application.dto.request;

import co.edu.uniquindio.sga.application.dto.validation.FechaNacimientoRazonable;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * DTO de entrada del registro público (Guía 12, 7.5.2). No trae rol: el registro siempre crea huéspedes.
 * Los @Size(max) son los de las columnas de UsuarioEntity; el de la contraseña es el límite de BCrypt (72 bytes).
 */
public record RegistroUsuarioRequest(
        @NotBlank(message = "El nombre es obligatorio")
        @Pattern(regexp = ReglaNombre.PATRON, message = ReglaNombre.MENSAJE)
        String nombre,

        @NotBlank(message = "El correo es obligatorio")
        @Email(regexp = ReglaTexto.PATRON_CORREO, message = "El correo no tiene un formato válido")
        @Size(max = 120, message = "El correo admite máximo 120 caracteres")
        String correo,

        @NotBlank(message = "La contraseña es obligatoria")
        @Size(min = 8, max = 72, message = "La contraseña debe tener entre 8 y 72 caracteres")
        @Pattern(regexp = "^(?=.*\\p{Lu})(?=.*\\d).*$",
                message = "La contraseña debe tener al menos una mayúscula y un número")
        String contrasena,

        @NotBlank(message = "El teléfono es obligatorio")
        @Pattern(regexp = ReglaTelefono.PATRON, message = ReglaTelefono.MENSAJE)
        String telefono,

        @NotBlank(message = "El documento es obligatorio")
        @Pattern(regexp = ReglaDocumento.PATRON, message = ReglaDocumento.MENSAJE)
        String documento,

        @NotNull(message = "La fecha de nacimiento es obligatoria")
        @Past(message = "La fecha de nacimiento debe ser pasada")
        @FechaNacimientoRazonable
        LocalDate fechaNacimiento
) {
}

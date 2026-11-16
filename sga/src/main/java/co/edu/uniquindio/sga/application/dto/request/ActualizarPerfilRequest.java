package co.edu.uniquindio.sga.application.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * DTO de entrada de PUT /api/usuarios/me (Guía 12, 7.2). Solo trae lo que el usuario puede editar de su perfil:
 * el correo no es editable porque es el identificador del inicio de sesión (sub del token).
 */
public record ActualizarPerfilRequest(
        @NotBlank(message = "El nombre es obligatorio")
        @Pattern(regexp = ReglaNombre.PATRON, message = ReglaNombre.MENSAJE)
        String nombre,

        @NotBlank(message = "El teléfono es obligatorio")
        @Pattern(regexp = ReglaTelefono.PATRON, message = ReglaTelefono.MENSAJE)
        String telefono,

        // Opcional: sin dato se quita la foto
        @Size(max = 500, message = "La URL de la foto admite máximo 500 caracteres")
        @Pattern(regexp = ReglaTexto.PATRON_URL_IMAGEN, message = ReglaTexto.MENSAJE_URL_IMAGEN)
        String fotoUrl
) {
}

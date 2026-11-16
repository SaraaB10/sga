package co.edu.uniquindio.sga.application.dto.request;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Titular de la reserva (3.2). Todos sus datos son obligatorios. El documento viaja como dos textos
 * (tipo y número) porque DocumentoIdentidad es un objeto de valor del dominio.
 */
public record TitularRequest(
    @NotBlank(message = "El nombre del titular es obligatorio")
    @Pattern(regexp = ReglaNombre.PATRON, message = ReglaNombre.MENSAJE)
    String nombre,

    @NotBlank(message = "El tipo de documento del titular es obligatorio")
    @Size(max = 20, message = "El tipo de documento admite máximo 20 caracteres")
    String tipoDocumento,

    @NotBlank(message = "El número de documento del titular es obligatorio")
    @Pattern(regexp = ReglaDocumento.PATRON, message = ReglaDocumento.MENSAJE)
    String numeroDocumento,

    @NotBlank(message = "El email del titular es obligatorio")
    @Email(regexp = ReglaTexto.PATRON_CORREO, message = "El email del titular no tiene un formato válido")
    @Size(max = 120, message = "El email del titular admite máximo 120 caracteres")
    String email,

    @NotBlank(message = "El teléfono del titular es obligatorio")
    @Pattern(regexp = ReglaTelefono.PATRON, message = ReglaTelefono.MENSAJE)
    String telefono
) {
}

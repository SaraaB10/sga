package co.edu.uniquindio.sga.application.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de POST /api/alojamiento/servicios. Solo valida la forma del mensaje; que el id no se repita y que un
 * servicio con cargo tenga valor mayor que cero lo valida el dominio (409).
 */
public record RegistrarServicioAdicionalRequest(
        @NotBlank(message = "El id del servicio es obligatorio")
        @Size(max = 40, message = "El id admite máximo 40 caracteres")
        String id,                               // SRV-SPA
        @NotBlank(message = "El nombre del servicio es obligatorio")
        @Size(max = 100, message = "El nombre admite máximo 100 caracteres")
        String nombre,
        @Size(max = 300, message = "La descripción admite máximo 300 caracteres")
        String descripcion,
        @NotNull(message = "Indique si el servicio genera cargo")
        Boolean generaCargo,
        @PositiveOrZero(message = "El valor no puede ser negativo")
        Long valor                               // pesos enteros; se ignora si no genera cargo
) {
}

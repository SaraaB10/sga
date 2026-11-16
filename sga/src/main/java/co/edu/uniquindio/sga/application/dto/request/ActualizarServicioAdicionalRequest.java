package co.edu.uniquindio.sga.application.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de PUT /api/alojamiento/servicios/{id}: reemplaza nombre, descripción, si genera cargo y valor. El id no se
 * cambia. Las reglas (valor mayor que cero si genera cargo, RP-03) las valida el dominio (409).
 */
public record ActualizarServicioAdicionalRequest(
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

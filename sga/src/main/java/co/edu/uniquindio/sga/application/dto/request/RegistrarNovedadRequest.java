package co.edu.uniquindio.sga.application.dto.request;

import co.edu.uniquindio.sga.domain.valueobject.GravedadNovedad;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * CU-17. POST /api/apartamentos/{id}/novedades
 * Daño, faltante o incidencia del apartamento. La fecha la pone el SGA con el reloj de Colombia, no el cliente.
 */
public record RegistrarNovedadRequest(
        @NotBlank(message = "El autor de la novedad es obligatorio")
        @Pattern(regexp = ReglaNombre.PATRON, message = ReglaNombre.MENSAJE)
        String autor,

        @NotBlank(message = "La descripción de la novedad es obligatoria")
        @Size(max = ReglaTexto.MAXIMO_DESCRIPCION, message = "La descripción admite máximo 1000 caracteres")
        String descripcion,

        @NotNull(message = "La gravedad de la novedad es obligatoria")
        GravedadNovedad gravedad                 // BAJA, MEDIA o ALTA
) {
}
package co.edu.uniquindio.sga.application.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * CU-23. PUT /api/apartamentos/{id}
 * Reemplaza nombre, descripción, dormitorios, dotación y capacidad. Las imágenes, tarifas y estado no cambian aquí.
 * Este DTO solo revisa la forma (400): obligatorios, largos máximos y textos que no quedan solo en espacios. Los
 * mínimos de capacidad, dormitorios y dotación los valida el dominio (409).
 */
public record ActualizarApartamentoRequest(
        @NotBlank(message = "El nombre del apartamento es obligatorio")
        @Size(max = ReglaTexto.MAXIMO_NOMBRE, message = "El nombre admite máximo 100 caracteres")
        String nombre,

        @Size(max = ReglaTexto.MAXIMO_DESCRIPCION, message = "La descripción admite máximo 1000 caracteres")
        @Pattern(regexp = ReglaTexto.PATRON_NO_SOLO_ESPACIOS, message = "La descripción no puede quedar solo en espacios")
        String descripcion,

        @NotNull(message = "El número de dormitorios es obligatorio")
        Integer dormitorios,

        @NotNull(message = "La capacidad máxima es obligatoria")
        Integer capacidadMaxima,

        @NotNull(message = "La dotación es obligatoria")
        List<@Size(max = ReglaTexto.MAXIMO_NOMBRE,
                message = "Cada elemento de la dotación admite máximo 100 caracteres") String> dotacion
) {
}

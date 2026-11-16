package co.edu.uniquindio.sga.application.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * CU-22. POST /api/apartamentos
 * Datos del apartamento nuevo; las tarifas son opcionales. No trae el estado: nace PREPARADO e INACTIVO (7.4).
 * Este DTO solo revisa la forma (400): obligatorios, largos máximos, textos que no quedan solo en espacios y URL de
 * las imágenes. La identificación única (L-04), la capacidad y los dormitorios mínimos, la dotación no vacía, las
 * imágenes 1..10 con una principal (7.3) y las tarifas (7.4) las valida el dominio (409).
 */
public record RegistrarApartamentoRequest(
        @NotBlank(message = "La identificación del apartamento es obligatoria")
        @Size(max = 20, message = "La identificación admite máximo 20 caracteres")
        String identificacion,

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
                message = "Cada elemento de la dotación admite máximo 100 caracteres") String> dotacion,

        // Opcional: rasgos del apartamento (Vista al mar, Balcón...), distintos de la dotación
        @Size(max = 20, message = "El apartamento admite máximo 20 características")
        List<@NotBlank(message = "Las características no pueden estar vacías")
             @Size(min = 2, max = 60, message = "Cada característica debe tener entre 2 y 60 caracteres") String>
                caracteristicas,

        @NotNull(message = "Las imágenes son obligatorias")
        List<@Valid @NotNull(message = "La imagen no puede ser nula") ImagenRequest> imagenes,

        List<@Valid @NotNull(message = "La tarifa no puede ser nula") TarifaRequest> tarifas
) {
}

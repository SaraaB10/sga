package co.edu.uniquindio.sga.application.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalTime;

/**
 * CU-28. PUT /api/alojamiento
 * Reemplaza nombre, descripción, ciudad, dirección, ubicación y normas de convivencia del alojamiento. El horario
 * de entrada y salida es opcional: si no llega ninguna de las dos horas se conserva el vigente.
 * Los parámetros de la sección 6 no se cambian aquí: viven en application.properties.
 * Este DTO solo revisa la forma (400): obligatorios, largos máximos y textos que no quedan solo en espacios. Los
 * rangos de la coordenada (7.3) y el horario completo (L-12) los valida el dominio (409).
 */
public record ActualizarAlojamientoRequest(
        @NotBlank(message = "El nombre del alojamiento es obligatorio")
        @Size(max = ReglaTexto.MAXIMO_NOMBRE, message = "El nombre admite máximo 100 caracteres")
        String nombre,

        @Size(max = ReglaTexto.MAXIMO_DESCRIPCION, message = "La descripción admite máximo 1000 caracteres")
        @Pattern(regexp = ReglaTexto.PATRON_NO_SOLO_ESPACIOS, message = "La descripción no puede quedar solo en espacios")
        String descripcion,

        @NotBlank(message = "La ciudad del alojamiento es obligatoria")
        @Size(max = 80, message = "La ciudad admite máximo 80 caracteres")
        String ciudad,

        @Size(max = 200, message = "La dirección admite máximo 200 caracteres")
        @Pattern(regexp = ReglaTexto.PATRON_NO_SOLO_ESPACIOS, message = "La dirección no puede quedar solo en espacios")
        String direccion,

        @NotNull(message = "La latitud es obligatoria")
        Double latitud,

        @NotNull(message = "La longitud es obligatoria")
        Double longitud,

        @Size(max = ReglaTexto.MAXIMO_DESCRIPCION, message = "Las normas de convivencia admiten máximo 1000 caracteres")
        @Pattern(regexp = ReglaTexto.PATRON_NO_SOLO_ESPACIOS,
                message = "Las normas de convivencia no pueden quedar solo en espacios")
        String normaConvivencia,

        LocalTime horaEntrada,

        LocalTime horaSalida
) {
}

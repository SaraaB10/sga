package co.edu.uniquindio.sga.application.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * CU-22. Una imagen del apartamento (7.3). La URL empieza por https:// (400); que haya entre 1 y 10 imágenes y que
 * exactamente una sea la principal lo valida el dominio (409).
 */
public record ImagenRequest(
        @NotBlank(message = "La URL de la imagen es obligatoria")
        @Size(max = 500, message = "La URL de la imagen admite máximo 500 caracteres")
        @Pattern(regexp = ReglaTexto.PATRON_URL_IMAGEN, message = ReglaTexto.MENSAJE_URL_IMAGEN)
        String url,

        boolean principal
) {
}

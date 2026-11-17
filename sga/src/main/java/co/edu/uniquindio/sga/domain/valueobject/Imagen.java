package co.edu.uniquindio.sga.domain.valueobject;

import co.edu.uniquindio.sga.domain.exception.ReglaDominioException;

import java.util.regex.Pattern;

/**
 * Imagen de un apartamento almacenada en un servicio externo (7.3).
 * El mínimo de 1, el máximo de 10 y la imagen principal única los valida Apartamento.
 * La URL empieza por https://. La única excepción es http://localhost o http://127.0.0.1: son las fotos que guarda
 * el almacén local cuando el backend corre en el propio equipo.
 */
public record Imagen(String url, boolean esPrincipal) {

    private static final Pattern URL_VALIDA =
            Pattern.compile("^(?:https://\\S+|http://(?:localhost|127\\.0\\.0\\.1)(?::\\d{1,5})?/\\S*)$");

    public Imagen {
        if (url == null || url.isBlank()) {
            throw new ReglaDominioException("7.3: la imagen debe tener una URL");
        }
        url = url.trim();
        if (!URL_VALIDA.matcher(url).matches()) {
            throw new ReglaDominioException("7.3: la URL de la imagen debe empezar con https:// y no tener espacios");
        }
    }
}

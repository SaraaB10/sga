package co.edu.uniquindio.sga.domain.exception;

/**
 * El almacén de imágenes no pudo guardar la foto: el servicio no respondió, tardó demasiado, rechazó la petición o
 * faltan sus credenciales. Es una falla técnica de la integración, no una regla del negocio; el adaptador REST la
 * traduce a 502 y el apartamento se puede registrar igual con las demás fotos o con una URL.
 */
public class ImagenNoAlmacenadaException extends RuntimeException {

    public ImagenNoAlmacenadaException(String message) {
        super(message);
    }

    public ImagenNoAlmacenadaException(String message, Throwable causa) {
        super(message, causa);
    }
}

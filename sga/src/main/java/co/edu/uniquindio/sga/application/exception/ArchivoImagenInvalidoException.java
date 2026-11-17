package co.edu.uniquindio.sga.application.exception;

/**
 * El archivo que se quiere subir no sirve como foto: está vacío, supera el tamaño máximo o no es JPEG, PNG ni WebP.
 * El adaptador REST la traduce a 400 con el error en el campo "archivo".
 */
public class ArchivoImagenInvalidoException extends RuntimeException {

    public ArchivoImagenInvalidoException(String message) {
        super(message);
    }
}

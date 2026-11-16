package co.edu.uniquindio.sga.application.exception;

/**
 * La contraseña nueva del cambio de contraseña es la misma que ya tiene la cuenta: se compara con el hash guardado.
 * Es un dato de entrada errado del propio usuario: el adaptador REST la traduce a 400 sobre el campo contrasenaNueva.
 */
public class ContrasenaNuevaIgualALaActualException extends RuntimeException {

    public static final String MENSAJE = "La contraseña nueva debe ser distinta de la actual";

    public ContrasenaNuevaIgualALaActualException() {
        super(MENSAJE);
    }
}

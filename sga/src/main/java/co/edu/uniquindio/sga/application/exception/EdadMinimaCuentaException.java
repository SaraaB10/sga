package co.edu.uniquindio.sga.application.exception;

/**
 * Quien intenta crear una cuenta (registro público o alta por el administrador) no tiene la edad mínima.
 * La API la responde como un dato inválido del campo fechaNacimiento (400).
 */
public class EdadMinimaCuentaException extends RuntimeException {

    public EdadMinimaCuentaException(int edadMinima) {
        super("Debes tener al menos " + edadMinima + " años para crear una cuenta");
    }
}

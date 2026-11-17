package co.edu.uniquindio.sga.application.exception;

/**
 * El administrador intenta cambiar su propio rol: podría quedarse sin permisos de administración. Excepción de
 * aplicación: el dominio no conoce al usuario (3.6). El adaptador REST la traduce a 409.
 */
public class RolPropioNoModificableException extends RuntimeException {

    public static final String MENSAJE = "El administrador no puede cambiar su propio rol";

    public RolPropioNoModificableException() {
        super(MENSAJE);
    }
}

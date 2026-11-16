package co.edu.uniquindio.sga.application.exception;

/**
 * El medio de pago pedido no está en el catálogo (L-18). Excepción de aplicación: la lanza el caso de uso. El
 * adaptador REST la traduce a 404.
 */
public class MedioPagoNoEncontradoException extends RuntimeException {

    public MedioPagoNoEncontradoException(String codigo) {
        super("No se encontró el medio de pago con código: " + (codigo == null ? "(vacío)" : codigo));
    }
}

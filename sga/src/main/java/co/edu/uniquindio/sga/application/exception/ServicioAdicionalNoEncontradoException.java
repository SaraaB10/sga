package co.edu.uniquindio.sga.application.exception;

/**
 * El servicio adicional pedido no existe en el alojamiento. Es una excepción de aplicación: la lanza el caso de uso
 * antes de llamar al dominio. El adaptador REST la traduce a 404.
 */
public class ServicioAdicionalNoEncontradoException extends RuntimeException {

    public ServicioAdicionalNoEncontradoException(String idServicio) {
        super("No se encontró el servicio adicional con id: " + (idServicio == null ? "(vacío)" : idServicio));
    }
}

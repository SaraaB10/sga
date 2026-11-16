package co.edu.uniquindio.sga.domain.exception;

import co.edu.uniquindio.sga.domain.valueobject.Titular;

/**
 * En una reserva tomada por la recepción (canal DIRECTO) el titular no alcanza la edad mínima para responder por
 * ella. Es una regla del dominio, pero lo que llega mal es la fecha de nacimiento del titular: la API la responde
 * como un dato inválido de ese campo (400), no como un conflicto con el estado del sistema.
 */
public class TitularMenorDeEdadException extends ReglaDominioException {

    public static final String MENSAJE = "El titular debe tener al menos " + Titular.EDAD_MINIMA + " años";

    public TitularMenorDeEdadException() {
        super(MENSAJE);
    }
}

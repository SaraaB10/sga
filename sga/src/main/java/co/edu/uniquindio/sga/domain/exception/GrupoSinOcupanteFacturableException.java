package co.edu.uniquindio.sga.domain.exception;

/**
 * El grupo no tiene ningún ocupante que alcance el umbral de edad facturable. Toda estancia necesita al menos un
 * adulto que pague noche, porque el titular es siempre un ocupante facturable. Es una regla del dominio, pero la API
 * la responde como un dato mal formado del grupo (400), no como un conflicto con el estado del sistema.
 */
public class GrupoSinOcupanteFacturableException extends ReglaDominioException {

    public static final String MENSAJE =
            "El grupo debe incluir al menos un adulto que pague noche; el titular siempre es facturable";

    public GrupoSinOcupanteFacturableException() {
        super(MENSAJE);
    }
}

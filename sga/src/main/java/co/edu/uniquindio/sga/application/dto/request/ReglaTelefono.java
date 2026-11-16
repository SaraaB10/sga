package co.edu.uniquindio.sga.application.dto.request;

/**
 * Regla del teléfono en los mensajes de la API (cuentas, perfil y titular de una reserva). Es la misma que valida el
 * frontend (frontend/src/lib/validacion.ts): al menos 7 dígitos y solo números, espacios, guiones, paréntesis y un
 * signo + al inicio. El vacío pasa el patrón a propósito: de él se encarga @NotBlank, así cada error tiene un solo
 * mensaje.
 */
public final class ReglaTelefono {

    /** Vacío, o + opcional seguido de al menos 7 dígitos con espacios, guiones o paréntesis entre ellos */
    public static final String PATRON = "^\\s*$|^\\+?(?:[ ()-]*\\d){7,}[ ()-]*$";

    public static final String MENSAJE = "El teléfono debe tener al menos 7 dígitos y solo admite números, espacios, "
            + "guiones, paréntesis y el signo + al inicio";

    /** Longitud máxima, la de la columna del usuario */
    public static final int MAXIMO = 20;

    public static final String MENSAJE_MAXIMO = "El teléfono admite máximo 20 caracteres";

    private ReglaTelefono() {
    }
}

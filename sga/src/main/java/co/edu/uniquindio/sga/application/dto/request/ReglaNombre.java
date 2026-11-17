package co.edu.uniquindio.sga.application.dto.request;

/**
 * Regla del nombre de una persona en los mensajes de la API (registro, creación de usuarios, perfil, titular y
 * ocupantes de una reserva, canal externo y quien autoriza un cierre de folio). Es la misma que valida el frontend
 * (frontend/src/lib/validacion.ts): solo letras (con tildes y ñ) y espacios, entre 3 y 80 caracteres, sin espacios al
 * inicio ni al final. El vacío pasa el patrón a propósito: de él se encarga @NotBlank, así cada error tiene un solo
 * mensaje.
 */
public final class ReglaNombre {

    /** Vacío o solo espacios (los marca @NotBlank), o de 3 a 80 letras y espacios que empiezan y terminan en letra */
    public static final String PATRON = "^\\s*$|^(?=.{3,80}$)\\p{L}(?:[\\p{L} ]*\\p{L})?$";

    public static final String MENSAJE = "El nombre debe tener entre 3 y 80 caracteres, solo letras y espacios, "
            + "sin espacios al inicio ni al final";

    private ReglaNombre() {
    }
}

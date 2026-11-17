package co.edu.uniquindio.sga.application.dto.request;

/**
 * Regla del teléfono en los mensajes de la API (registro, creación de usuarios, perfil, titular de una reserva y
 * canal externo). Es la misma que valida el frontend (frontend/src/lib/validacion.ts): exactamente 10 dígitos, sin
 * espacios ni símbolos. El vacío pasa el patrón a propósito: de él se encarga @NotBlank, así cada error tiene un solo
 * mensaje.
 */
public final class ReglaTelefono {

    /** Vacío o solo espacios (los marca @NotBlank), o exactamente 10 dígitos */
    public static final String PATRON = "^\\s*$|^\\d{10}$";

    public static final String MENSAJE = "El teléfono debe tener 10 dígitos, solo números";

    private ReglaTelefono() {
    }
}

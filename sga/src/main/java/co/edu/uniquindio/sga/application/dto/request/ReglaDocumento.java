package co.edu.uniquindio.sga.application.dto.request;

/**
 * Regla del número de documento en los mensajes de la API (registro, creación de usuarios, titular y ocupantes de una
 * reserva y canal externo). Es la misma que valida el frontend (frontend/src/lib/validacion.ts): entre 6 y 10 dígitos,
 * sin espacios ni símbolos. El vacío pasa el patrón a propósito: donde el documento es obligatorio se encarga
 * @NotBlank, y en los ocupantes el documento es opcional.
 */
public final class ReglaDocumento {

    /** Vacío o solo espacios (sin documento, o lo marca @NotBlank), o de 6 a 10 dígitos */
    public static final String PATRON = "^\\s*$|^\\d{6,10}$";

    public static final String MENSAJE = "El documento debe tener entre 6 y 10 dígitos, solo números";

    private ReglaDocumento() {
    }
}

package co.edu.uniquindio.sga.application.dto.request;

/**
 * Reglas de formato compartidas por los mensajes de la API, las mismas del frontend (frontend/src/lib/validacion.ts):
 * correo con dominio y extensión, URL de imagen que empieza por https:// y textos libres que no quedan solo en
 * espacios.
 */
public final class ReglaTexto {

    /** Correo: usuario, @, dominio y al menos una extensión (ana@correo.co). Complementa a @Email */
    public static final String PATRON_CORREO = "^[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+$";

    /** URL de una imagen: https:// seguido de la dirección, sin espacios */
    public static final String PATRON_URL_IMAGEN = "^https://\\S+$";

    public static final String MENSAJE_URL_IMAGEN = "La URL de la imagen debe empezar por https:// y no tener espacios";

    /** Vacío (texto opcional sin dato) o con al menos un carácter que no sea espacio */
    public static final String PATRON_NO_SOLO_ESPACIOS = "(?s)^$|^.*\\S.*$";

    /** Largo máximo de los motivos (cancelación, bloqueo, corrección, cierre, no-show), el de su columna */
    public static final int MAXIMO_MOTIVO = 250;

    /** Largo máximo de los nombres de cosas (alojamiento, apartamento, temporada, elemento de dotación) */
    public static final int MAXIMO_NOMBRE = 100;

    /** Largo máximo de las descripciones y textos largos (apartamento, alojamiento, novedad, norma de convivencia) */
    public static final int MAXIMO_DESCRIPCION = 1000;

    private ReglaTexto() {
    }
}

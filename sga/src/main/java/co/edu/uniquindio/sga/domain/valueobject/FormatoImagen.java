package co.edu.uniquindio.sga.domain.valueobject;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;

/**
 * Formatos de foto que acepta el sistema para los apartamentos. El formato se reconoce por los primeros bytes del
 * archivo, no por el nombre ni por el tipo que declara el navegador, así un archivo renombrado no pasa por imagen.
 */
public enum FormatoImagen {

    JPEG("image/jpeg", "jpg"),
    PNG("image/png", "png"),
    WEBP("image/webp", "webp");

    private static final byte[] FIRMA_JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] FIRMA_PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
    private static final byte[] RIFF = "RIFF".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] WEBP_MARCA = "WEBP".getBytes(StandardCharsets.US_ASCII);

    private final String tipoContenido;
    private final String extension;

    FormatoImagen(String tipoContenido, String extension) {
        this.tipoContenido = tipoContenido;
        this.extension = extension;
    }

    public String tipoContenido() {
        return tipoContenido;
    }

    public String extension() {
        return extension;
    }

    /** El formato de la foto según su contenido, o vacío si no es JPEG, PNG ni WebP. */
    public static Optional<FormatoImagen> detectar(byte[] contenido) {
        if (contenido == null) {
            return Optional.empty();
        }
        if (empiezaCon(contenido, 0, FIRMA_JPEG)) {
            return Optional.of(JPEG);
        }
        if (empiezaCon(contenido, 0, FIRMA_PNG)) {
            return Optional.of(PNG);
        }
        if (empiezaCon(contenido, 0, RIFF) && empiezaCon(contenido, 8, WEBP_MARCA)) {
            return Optional.of(WEBP);
        }
        return Optional.empty();
    }

    /** El formato que corresponde a una extensión (jpg, png o webp), o vacío. */
    public static Optional<FormatoImagen> porExtension(String extension) {
        return Arrays.stream(values()).filter(f -> f.extension.equalsIgnoreCase(extension)).findFirst();
    }

    private static boolean empiezaCon(byte[] contenido, int desde, byte[] firma) {
        if (contenido.length < desde + firma.length) {
            return false;
        }
        for (int i = 0; i < firma.length; i++) {
            if (contenido[desde + i] != firma[i]) {
                return false;
            }
        }
        return true;
    }
}

package co.edu.uniquindio.sga.infrastructure.imagenes;

import co.edu.uniquindio.sga.domain.exception.ImagenNoAlmacenadaException;
import co.edu.uniquindio.sga.domain.port.AlmacenImagenes;
import co.edu.uniquindio.sga.domain.valueobject.FormatoImagen;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Adaptador LOCAL del puerto AlmacenImagenes (sga.imagenes.proveedor=local, el de por defecto y el de las pruebas):
 * guarda la foto en una carpeta del servidor (sga.imagenes.local.directorio) y la publica en
 * {sga.imagenes.local.url-publica}/api/imagenes/{nombre}, que sirve ImagenController. No usa internet ni
 * credenciales. El nombre del archivo es un UUID con la extensión del formato, nunca el nombre que trae el usuario.
 */
public class AlmacenImagenesLocal implements AlmacenImagenes {

    private static final Logger log = LoggerFactory.getLogger(AlmacenImagenesLocal.class);

    /** Ruta con la que el backend sirve las fotos guardadas aquí. */
    public static final String RUTA_PUBLICA = "/api/imagenes/";

    /** Solo nombres generados por este adaptador: así no se puede pedir un archivo fuera de la carpeta. */
    private static final Pattern NOMBRE_VALIDO = Pattern.compile("^[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}\\.(jpg|png|webp)$");

    private final Path directorio;
    private final String urlPublica;

    /**
     * @param directorio carpeta donde se guardan las fotos; se crea si no existe.
     * @param urlPublica dirección del backend con la que se arma la URL de cada foto, por ejemplo
     *                   http://localhost:8080. Sin barra al final.
     */
    public AlmacenImagenesLocal(Path directorio, String urlPublica) {
        this.directorio = Objects.requireNonNull(directorio).toAbsolutePath().normalize();
        String base = Objects.requireNonNull(urlPublica).trim();
        this.urlPublica = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    @Override
    public String guardar(byte[] contenido, FormatoImagen formato) {
        String nombre = UUID.randomUUID() + "." + formato.extension();
        try {
            Files.createDirectories(directorio);
            Files.write(directorio.resolve(nombre), contenido);
        } catch (IOException e) {
            log.warn("Imágenes: no se pudo guardar la foto en {}: {}", directorio, e.getMessage());
            throw new ImagenNoAlmacenadaException("No se pudo guardar la foto en el servidor", e);
        }
        log.info("Imágenes: foto guardada en el disco local como {}", nombre);
        return urlPublica + RUTA_PUBLICA + nombre;
    }

    /** La foto guardada con ese nombre, o vacío si el nombre no es válido o el archivo no existe. */
    public Optional<ImagenGuardada> leer(String nombre) {
        Matcher coincidencia = NOMBRE_VALIDO.matcher(nombre == null ? "" : nombre);
        if (!coincidencia.matches()) {
            return Optional.empty();
        }
        Path archivo = directorio.resolve(nombre);
        if (!Files.isRegularFile(archivo)) {
            return Optional.empty();
        }
        try {
            FormatoImagen formato = FormatoImagen.porExtension(coincidencia.group(1)).orElseThrow();
            return Optional.of(new ImagenGuardada(Files.readAllBytes(archivo), formato));
        } catch (IOException e) {
            log.warn("Imágenes: no se pudo leer la foto {}: {}", nombre, e.getMessage());
            return Optional.empty();
        }
    }

    /** Contenido y formato de una foto guardada en el disco. */
    public record ImagenGuardada(byte[] contenido, FormatoImagen formato) {
    }
}

package co.edu.uniquindio.sga.application.usecase;

import co.edu.uniquindio.sga.application.exception.ArchivoImagenInvalidoException;
import co.edu.uniquindio.sga.domain.port.AlmacenImagenes;
import co.edu.uniquindio.sga.domain.valueobject.FormatoImagen;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * POST /api/imagenes
 * Sube una foto de apartamento al almacén de imágenes configurado y devuelve su URL. No toca ningún apartamento:
 * el formulario usa esa URL al registrar, igual que una URL pegada a mano. Si el almacén falla, la excepción llega
 * al cliente como 502 y el formulario sigue con las demás fotos.
 * Actor: administrador.
 */
@Service
public class SubirImagenApartamentoUseCase {

    /** Tamaño máximo de una foto: 5 MB. */
    public static final long TAMANO_MAXIMO_BYTES = 5L * 1024 * 1024;

    private final AlmacenImagenes almacen;

    public SubirImagenApartamentoUseCase(AlmacenImagenes almacen) {
        this.almacen = Objects.requireNonNull(almacen);
    }

    /** @return la URL pública de la foto guardada. */
    public String ejecutar(byte[] contenido) {
        if (contenido == null || contenido.length == 0) {
            throw new ArchivoImagenInvalidoException("Selecciona una foto");
        }
        if (contenido.length > TAMANO_MAXIMO_BYTES) {
            throw new ArchivoImagenInvalidoException("La foto supera los 5 MB");
        }
        FormatoImagen formato = FormatoImagen.detectar(contenido)
                .orElseThrow(() -> new ArchivoImagenInvalidoException("La foto debe ser JPG, PNG o WebP"));
        return almacen.guardar(contenido, formato);
    }
}

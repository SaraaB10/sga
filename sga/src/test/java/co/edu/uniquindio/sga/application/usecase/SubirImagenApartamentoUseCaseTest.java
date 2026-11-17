package co.edu.uniquindio.sga.application.usecase;

import co.edu.uniquindio.sga.application.exception.ArchivoImagenInvalidoException;
import co.edu.uniquindio.sga.domain.exception.ImagenNoAlmacenadaException;
import co.edu.uniquindio.sga.infrastructure.imagenes.AlmacenImagenesLocal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Subida de una foto con el almacén local: valida el archivo antes de guardarlo. */
class SubirImagenApartamentoUseCaseTest {

    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16, 'J', 'F', 'I', 'F'};

    @TempDir
    Path carpeta;

    @Test
    @DisplayName("Una foto JPEG válida se guarda y devuelve su URL con la extensión del formato")
    void subeUnaFoto() {
        SubirImagenApartamentoUseCase caso = new SubirImagenApartamentoUseCase(
                new AlmacenImagenesLocal(carpeta, "http://localhost:8080"));

        String url = caso.ejecutar(JPEG);

        assertTrue(url.startsWith("http://localhost:8080/api/imagenes/") && url.endsWith(".jpg"), url);
    }

    @Test
    @DisplayName("Un archivo vacío, de más de 5 MB o que no es foto se rechaza sin guardar nada")
    void rechazaArchivosInvalidos() {
        SubirImagenApartamentoUseCase caso = new SubirImagenApartamentoUseCase((contenido, formato) -> {
            throw new AssertionError("No debía llegar al almacén");
        });
        byte[] grande = Arrays.copyOf(JPEG, (int) SubirImagenApartamentoUseCase.TAMANO_MAXIMO_BYTES + 1);

        assertEquals("Selecciona una foto",
                assertThrows(ArchivoImagenInvalidoException.class, () -> caso.ejecutar(new byte[0])).getMessage());
        assertEquals("La foto supera los 5 MB",
                assertThrows(ArchivoImagenInvalidoException.class, () -> caso.ejecutar(grande)).getMessage());
        assertEquals("La foto debe ser JPG, PNG o WebP", assertThrows(ArchivoImagenInvalidoException.class,
                () -> caso.ejecutar("hola".getBytes(StandardCharsets.UTF_8))).getMessage());
    }

    @Test
    @DisplayName("Si el almacén falla, la falla llega tal cual para que la pantalla avise")
    void propagaLaFallaDelAlmacen() {
        SubirImagenApartamentoUseCase caso = new SubirImagenApartamentoUseCase((contenido, formato) -> {
            throw new ImagenNoAlmacenadaException("No se pudo subir la foto a Cloudinary");
        });

        assertThrows(ImagenNoAlmacenadaException.class, () -> caso.ejecutar(JPEG));
    }
}

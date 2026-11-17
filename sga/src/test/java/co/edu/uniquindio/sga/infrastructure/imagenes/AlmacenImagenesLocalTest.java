package co.edu.uniquindio.sga.infrastructure.imagenes;

import co.edu.uniquindio.sga.domain.valueobject.FormatoImagen;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Adaptador local del almacén de imágenes: guarda en una carpeta temporal y sirve solo los nombres que generó. */
class AlmacenImagenesLocalTest {

    static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0, 0, 13, 'I', 'H', 'D', 'R'};

    @TempDir
    Path carpeta;

    @Test
    @DisplayName("Guarda la foto en la carpeta con un nombre aleatorio y la publica en /api/imagenes/{nombre}")
    void guardaYPublica() throws IOException {
        AlmacenImagenesLocal almacen = new AlmacenImagenesLocal(carpeta, "http://localhost:8080/");

        String url = almacen.guardar(PNG, FormatoImagen.PNG);

        assertTrue(url.matches("^http://localhost:8080/api/imagenes/[0-9a-f-]{36}\\.png$"), url);
        String nombre = url.substring(url.lastIndexOf('/') + 1);
        assertArrayEquals(PNG, Files.readAllBytes(carpeta.resolve(nombre)));
    }

    @Test
    @DisplayName("Lee la foto guardada con su formato")
    void leeLaFotoGuardada() {
        AlmacenImagenesLocal almacen = new AlmacenImagenesLocal(carpeta, "http://localhost:8080");
        String url = almacen.guardar(PNG, FormatoImagen.PNG);

        AlmacenImagenesLocal.ImagenGuardada imagen = almacen.leer(url.substring(url.lastIndexOf('/') + 1))
                .orElseThrow();

        assertEquals(FormatoImagen.PNG, imagen.formato());
        assertArrayEquals(PNG, imagen.contenido());
    }

    @Test
    @DisplayName("No sirve nombres inventados ni rutas fuera de la carpeta")
    void rechazaNombresAjenos() throws IOException {
        Files.writeString(carpeta.resolve("secreto.txt"), "no");
        AlmacenImagenesLocal almacen = new AlmacenImagenesLocal(carpeta.resolve("fotos"), "http://localhost:8080");

        assertTrue(almacen.leer("../secreto.txt").isEmpty());
        assertTrue(almacen.leer("secreto.txt").isEmpty());
        assertTrue(almacen.leer("00000000-0000-0000-0000-000000000000.png").isEmpty());
        assertTrue(almacen.leer(null).isEmpty());
    }
}

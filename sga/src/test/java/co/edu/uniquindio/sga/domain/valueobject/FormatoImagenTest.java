package co.edu.uniquindio.sga.domain.valueobject;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** El formato de una foto se reconoce por su contenido, no por el nombre del archivo. */
class FormatoImagenTest {

    @Test
    @DisplayName("Reconoce JPEG, PNG y WebP por sus primeros bytes")
    void reconoceLosFormatos() {
        assertEquals(Optional.of(FormatoImagen.JPEG),
                FormatoImagen.detectar(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0}));
        assertEquals(Optional.of(FormatoImagen.PNG),
                FormatoImagen.detectar(new byte[]{(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0}));
        assertEquals(Optional.of(FormatoImagen.WEBP),
                FormatoImagen.detectar("RIFF\0\0\0\0WEBPVP8 ".getBytes(StandardCharsets.US_ASCII)));
    }

    @Test
    @DisplayName("Un texto, un archivo corto o vacío no es una foto")
    void rechazaLoQueNoEsFoto() {
        assertTrue(FormatoImagen.detectar("<svg></svg>".getBytes(StandardCharsets.UTF_8)).isEmpty());
        assertTrue(FormatoImagen.detectar("RIFF\0\0\0\0WAVE".getBytes(StandardCharsets.US_ASCII)).isEmpty());
        assertTrue(FormatoImagen.detectar(new byte[]{(byte) 0xFF}).isEmpty());
        assertTrue(FormatoImagen.detectar(new byte[0]).isEmpty());
        assertTrue(FormatoImagen.detectar(null).isEmpty());
    }
}

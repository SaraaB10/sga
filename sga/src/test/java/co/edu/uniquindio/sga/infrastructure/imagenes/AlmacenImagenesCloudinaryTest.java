package co.edu.uniquindio.sga.infrastructure.imagenes;

import co.edu.uniquindio.sga.domain.exception.ImagenNoAlmacenadaException;
import co.edu.uniquindio.sga.domain.valueobject.FormatoImagen;
import co.edu.uniquindio.sga.infrastructure.ServidorHttpSimulado;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Adaptador de Cloudinary contra un servidor simulado: nunca sale a internet ni usa credenciales reales. */
class AlmacenImagenesCloudinaryTest {

    private static final Clock RELOJ = Clock.fixed(Instant.ofEpochSecond(1_700_000_000L), ZoneOffset.UTC);

    @Test
    @DisplayName("La firma es el SHA-1 de folder y timestamp en orden alfabético seguidos del secreto")
    void firmaDeLaSubida() {
        // sha1("folder=sga&timestamp=1700000000secreto")
        assertEquals("fb3ce2a49bcfd2d94321c271472e1cc1151fbbb3",
                AlmacenImagenesCloudinary.firma("sga", "1700000000", "secreto"));
        // Sin carpeta solo se firma el timestamp: sha1("timestamp=1700000000secreto")
        assertEquals("a768e4b42b39371bd715ef1658b12fcd8dff1258",
                AlmacenImagenesCloudinary.firma("", "1700000000", "secreto"));
    }

    @Test
    @DisplayName("Sube la foto firmada al cloud-name y devuelve la secure_url")
    void subeLaFotoFirmada() throws IOException {
        try (ServidorHttpSimulado servidor = new ServidorHttpSimulado()
                .responder(200, "{\"secure_url\":\"https://res.cloudinary.com/demo/image/upload/v1/sga/x.png\"}")) {
            AlmacenImagenesCloudinary almacen = new AlmacenImagenesCloudinary(servidor.url(), "demo", "clave-api",
                    "secreto", "sga/apartamentos", Duration.ofSeconds(5), RELOJ);

            String url = almacen.guardar(AlmacenImagenesLocalTest.PNG, FormatoImagen.PNG);

            assertEquals("https://res.cloudinary.com/demo/image/upload/v1/sga/x.png", url);
            ServidorHttpSimulado.Peticion peticion = servidor.peticiones().getFirst();
            assertEquals("/demo/image/upload", peticion.ruta());
            String firma = AlmacenImagenesCloudinary.firma("sga/apartamentos", "1700000000", "secreto");
            assertTrue(peticion.cuerpo().contains(firma), "La petición lleva la firma");
            assertTrue(peticion.cuerpo().contains("clave-api"), "La petición lleva la api-key");
            assertFalse(peticion.cuerpo().contains("secreto"), "El secreto nunca viaja");
        }
    }

    @Test
    @DisplayName("Si Cloudinary rechaza la subida, lanza ImagenNoAlmacenadaException")
    void fallaDeCloudinary() throws IOException {
        try (ServidorHttpSimulado servidor = new ServidorHttpSimulado()
                .responder(401, "{\"error\":{\"message\":\"Invalid Signature\"}}")) {
            AlmacenImagenesCloudinary almacen = new AlmacenImagenesCloudinary(servidor.url(), "demo", "clave-api",
                    "secreto", "", Duration.ofSeconds(5), RELOJ);

            assertThrows(ImagenNoAlmacenadaException.class,
                    () -> almacen.guardar(AlmacenImagenesLocalTest.PNG, FormatoImagen.PNG));
        }
    }

    @Test
    @DisplayName("Sin credenciales no llama a Cloudinary y lanza ImagenNoAlmacenadaException")
    void sinCredenciales() throws IOException {
        try (ServidorHttpSimulado servidor = new ServidorHttpSimulado()) {
            AlmacenImagenesCloudinary almacen = new AlmacenImagenesCloudinary(servidor.url(), "", "", "", "",
                    Duration.ofSeconds(5), RELOJ);

            assertThrows(ImagenNoAlmacenadaException.class,
                    () -> almacen.guardar(AlmacenImagenesLocalTest.PNG, FormatoImagen.PNG));
            assertTrue(servidor.peticiones().isEmpty());
        }
    }
}

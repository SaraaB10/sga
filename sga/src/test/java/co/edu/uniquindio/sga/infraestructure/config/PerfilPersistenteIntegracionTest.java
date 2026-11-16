package co.edu.uniquindio.sga.infraestructure.config;

import co.edu.uniquindio.sga.SgaApplication;
import co.edu.uniquindio.sga.application.dto.request.CrearUsuarioRequest;
import co.edu.uniquindio.sga.application.security.UsuarioService;
import co.edu.uniquindio.sga.domain.repository.ApartamentoRepository;
import co.edu.uniquindio.sga.domain.repository.ReservaRepository;
import co.edu.uniquindio.sga.domain.repository.ServicioAdicionalRepository;
import co.edu.uniquindio.sga.domain.repository.TemporadaRepository;
import co.edu.uniquindio.sga.infrastructure.persistence.jpa.UsuarioJpaDataRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Perfil "persistente": la base H2 queda en un archivo. Arranca la aplicación, crea una cuenta, la detiene y la
 * vuelve a arrancar sobre el mismo archivo: la cuenta sigue y los datos iniciales no se cargan otra vez.
 */
class PerfilPersistenteIntegracionTest {

    private static final String CORREO = "persistente@sga.local";

    @TempDir
    Path carpeta;

    @Test
    @DisplayName("Perfil persistente: los datos sobreviven al reinicio y los iniciales no se duplican")
    void losDatosSobrevivenAlReinicioSinDuplicarLosIniciales() {
        Map<String, Integer> alPrincipio;
        long usuariosConLaCuenta;

        try (ConfigurableApplicationContext app = arrancar()) {
            assertEquals("jdbc:h2:file:" + carpeta.resolve("sga"),
                    app.getBean(Environment.class).getProperty("spring.datasource.url"));
            alPrincipio = contar(app);
            assertTrue(alPrincipio.values().stream().allMatch(n -> n > 0), "Faltan datos iniciales: " + alPrincipio);

            app.getBean(UsuarioService.class).crear(new CrearUsuarioRequest("Cuenta Persistente", CORREO,
                    "Persistente2026", "3001234567", "70707070", LocalDate.of(1990, 5, 20), "HUESPED"));
            usuariosConLaCuenta = app.getBean(UsuarioJpaDataRepository.class).count();
        }
        assertTrue(Files.exists(carpeta.resolve("sga.mv.db")), "La base no quedó en el archivo");

        try (ConfigurableApplicationContext app = arrancar()) {
            UsuarioJpaDataRepository usuarios = app.getBean(UsuarioJpaDataRepository.class);
            assertTrue(usuarios.findByCorreo(CORREO).isPresent(), "La cuenta creada no sobrevivió al reinicio");
            assertEquals(usuariosConLaCuenta, usuarios.count(), "Se duplicaron los usuarios iniciales");

            Map<String, Integer> despues = contar(app);
            despues.remove("usuarios");
            Map<String, Integer> esperado = new HashMap<>(alPrincipio);
            esperado.remove("usuarios");
            assertEquals(esperado, despues, "Se duplicaron los datos iniciales");
        }
    }

    private ConfigurableApplicationContext arrancar() {
        // Como argumentos: mandan sobre application-persistente.properties, así la prueba no toca ./data
        return new SpringApplicationBuilder(SgaApplication.class)
                .profiles("persistente")
                .run("--spring.datasource.url=jdbc:h2:file:" + carpeta.resolve("sga"),
                        "--server.port=0",
                        "--sga.recordatorios.activo=false",
                        "--sga.ia.proveedor=local",
                        "--sga.notificaciones.proveedor=local");
    }

    private static Map<String, Integer> contar(ConfigurableApplicationContext app) {
        return new HashMap<>(Map.of(
                "usuarios", (int) app.getBean(UsuarioJpaDataRepository.class).count(),
                "apartamentos", app.getBean(ApartamentoRepository.class).listarNoEliminados().size(),
                "reservas", app.getBean(ReservaRepository.class).listarTodas().size(),
                "temporadas", app.getBean(TemporadaRepository.class).listarTodas().size(),
                "servicios", app.getBean(ServicioAdicionalRepository.class).listarTodos().size()));
    }
}

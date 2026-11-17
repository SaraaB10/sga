package co.edu.uniquindio.sga.infraestructure.config;

import co.edu.uniquindio.sga.infrastructure.config.setup.DefaultUserProperties;
import co.edu.uniquindio.sga.infrastructure.security.RolUsuario;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.PropertiesPropertySourceLoader;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * El perfil prod crea el administrador, la recepción y el personal de servicio con los datos de las variables de
 * entorno SGA_ADMIN_*, SGA_RECEPCION_* y SGA_SERVICIO_*; ningún dato de esas cuentas está en el repositorio.
 */
class UsuariosProduccionTest {

    @Test
    @DisplayName("application-prod.properties arma los tres usuarios iniciales desde las variables de entorno")
    void usuariosDesdeVariables() throws IOException {
        Map<String, Object> variables = new HashMap<>();
        for (String prefijo : List.of("ADMIN", "RECEPCION", "SERVICIO")) {
            variables.put("SGA_" + prefijo + "_NOMBRE", "Nombre " + prefijo);
            variables.put("SGA_" + prefijo + "_CORREO", prefijo.toLowerCase() + "@coral.co");
            variables.put("SGA_" + prefijo + "_CONTRASENA", "Clave" + prefijo + "2026");
            variables.put("SGA_" + prefijo + "_TELEFONO", "3001234567");
            variables.put("SGA_" + prefijo + "_DOCUMENTO", "10000" + prefijo.length());
            variables.put("SGA_" + prefijo + "_FECHA_NACIMIENTO", "1990-01-01");
        }
        StandardEnvironment entorno = new StandardEnvironment();
        entorno.getPropertySources().addFirst(new MapPropertySource("variables", variables));
        for (PropertySource<?> fuente : new PropertiesPropertySourceLoader()
                .load("prod", new ClassPathResource("application-prod.properties"))) {
            entorno.getPropertySources().addLast(fuente);
        }

        List<DefaultUserProperties.DefaultUser> usuarios = Binder.get(entorno)
                .bind("sga.usuarios-iniciales", DefaultUserProperties.class).get().usuarios();

        assertEquals(List.of(RolUsuario.ADMINISTRADOR, RolUsuario.RECEPCIONISTA, RolUsuario.PERSONAL_SERVICIO),
                usuarios.stream().map(DefaultUserProperties.DefaultUser::rol).toList());
        DefaultUserProperties.DefaultUser recepcion = usuarios.get(1);
        assertEquals("Nombre RECEPCION", recepcion.nombre());
        assertEquals("recepcion@coral.co", recepcion.correo());
        assertEquals("ClaveRECEPCION2026", recepcion.contrasena());
        assertEquals("1990-01-01", recepcion.fechaNacimiento());
        assertEquals("servicio@coral.co", usuarios.get(2).correo());
        assertEquals("Nombre SERVICIO", usuarios.get(2).nombre());
    }
}

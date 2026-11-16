package co.edu.uniquindio.sga.infrastructure.config.setup;

import co.edu.uniquindio.sga.infrastructure.persistence.jpa.UsuarioJpaDataRepository;
import co.edu.uniquindio.sga.infrastructure.persistence.jpa.entity.UsuarioEntity;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/** Carga un usuario por rol al iniciar (Guía 12, 12.3) */
@Component
@RequiredArgsConstructor
public class DefaultUserInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DefaultUserInitializer.class);

    private final DefaultUserProperties props;
    private final UsuarioJpaDataRepository repository;
    private final PasswordEncoder encoder;

    /**
     * Con la base vacía crea todos los usuarios iniciales. En una base que ya tiene usuarios solo completa los roles
     * que no tienen ninguno (al menos un usuario por rol), con los usuarios iniciales de ese rol cuyo correo y
     * documento no estén ya registrados: no duplica ni toca los que existen.
     */
    @Override
    public void run(String... args) {
        boolean vacia = repository.count() == 0;
        long antes = repository.count();
        props.usuarios().stream()
                .filter(inicial -> vacia || !repository.existsByRol(inicial.rol()))
                .filter(inicial -> !repository.existsByCorreo(inicial.correo())
                        && !repository.existsByDocumento(inicial.documento()))
                .forEach(this::crear);
        long creados = repository.count() - antes;
        if (creados > 0) {
            log.info("Usuarios iniciales cargados en H2: {} (total {})", creados, repository.count());
        }
    }

    private void crear(DefaultUserProperties.DefaultUser inicial) {
        UsuarioEntity usuario = new UsuarioEntity();
        usuario.setNombre(inicial.nombre());
        usuario.setCorreo(inicial.correo());
        usuario.setContrasena(encoder.encode(inicial.contrasena()));
        usuario.setTelefono(inicial.telefono());
        usuario.setDocumento(inicial.documento());
        usuario.setFechaNacimiento(LocalDate.parse(inicial.fechaNacimiento()));
        usuario.setRol(inicial.rol());
        repository.save(usuario);
    }
}

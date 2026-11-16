package co.edu.uniquindio.sga.application.dto.request;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regla del teléfono (ReglaTelefono), la misma del frontend: al menos 7 dígitos y solo números, espacios, guiones,
 * paréntesis y un + al inicio. Se prueba sobre los mensajes que la usan: registro, creación de usuario, perfil y
 * titular de la reserva.
 */
class ReglaTelefonoTest {

    private static ValidatorFactory fabrica;
    private static Validator validador;

    @BeforeAll
    static void crearValidador() {
        fabrica = Validation.buildDefaultValidatorFactory();
        validador = fabrica.getValidator();
    }

    @AfterAll
    static void cerrarValidador() {
        fabrica.close();
    }

    @ParameterizedTest(name = "\"{0}\" es válido")
    @ValueSource(strings = {"3001234567", "1234567", "+573001234567", "+57 300 123 4567", "(604) 123-4567",
            "300-123-4567", "+1 (555) 555-0707"})
    @DisplayName("Acepta teléfonos con al menos 7 dígitos y los separadores permitidos")
    void aceptaTelefonosValidos(String telefono) {
        assertTrue(erroresDeTelefono(telefono).isEmpty(), "Se rechazó " + telefono + ": " + erroresDeTelefono(telefono));
    }

    @ParameterizedTest(name = "\"{0}\" se rechaza")
    @ValueSource(strings = {"123456", "(300) 12", "300-ABC-4567", "300.123.4567", "300 123 4567 ext 2", "57+3001234567",
            "++573001234567", "+", "----------"})
    @DisplayName("Rechaza teléfonos con menos de 7 dígitos, letras, otros signos o un + fuera del inicio")
    void rechazaTelefonosInvalidos(String telefono) {
        assertEquals(List.of(ReglaTelefono.MENSAJE), erroresDeTelefono(telefono));
    }

    @Test
    @DisplayName("Vacío: solo el mensaje de obligatorio; más de 20 caracteres: solo el de la longitud")
    void cadaErrorTieneUnSoloMensaje() {
        assertEquals(List.of("El teléfono es obligatorio"), erroresDeTelefono(""));
        assertEquals(List.of("El teléfono es obligatorio"), erroresDeTelefono("   "));
        assertEquals(List.of(ReglaTelefono.MENSAJE_MAXIMO), erroresDeTelefono("+57 (300) 123-4567 890"));
    }

    @Test
    @DisplayName("La misma regla rige en la creación de usuarios, el perfil y el titular de la reserva")
    void laMismaReglaEnTodosLosMensajes() {
        String malo = "300-ABC-4567";
        assertEquals(Set.of(ReglaTelefono.MENSAJE), mensajes(validador.validate(new CrearUsuarioRequest("Ana Torres",
                "ana@correo.co", "Segura2026", malo, "1095000001", LocalDate.of(1990, 5, 20), "HUESPED"))));
        assertEquals(Set.of(ReglaTelefono.MENSAJE),
                mensajes(validador.validate(new ActualizarPerfilRequest("Ana Torres", malo, null))));
        assertEquals(Set.of(ReglaTelefono.MENSAJE),
                mensajes(validador.validate(new TitularRequest("Ana Torres", "CC", "1095000001", "ana@correo.co", malo))));
        assertTrue(validador.validate(new TitularRequest("Ana Torres", "CC", "1095000001", "ana@correo.co",
                "+57 300 123 4567")).isEmpty());
    }

    /** Mensajes de error del teléfono en un registro con los demás datos válidos */
    private static List<String> erroresDeTelefono(String telefono) {
        RegistroUsuarioRequest registro = new RegistroUsuarioRequest("Ana Torres", "ana@correo.co", "Segura2026",
                telefono, "1095000001", LocalDate.of(1990, 5, 20));
        return validador.validate(registro).stream()
                .filter(v -> v.getPropertyPath().toString().equals("telefono"))
                .map(ConstraintViolation::getMessage)
                .sorted()
                .toList();
    }

    private static Set<String> mensajes(Set<? extends ConstraintViolation<?>> violaciones) {
        return violaciones.stream().map(ConstraintViolation::getMessage).collect(Collectors.toSet());
    }
}

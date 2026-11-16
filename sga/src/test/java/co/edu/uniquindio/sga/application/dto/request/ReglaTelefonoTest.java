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
 * Regla del teléfono (ReglaTelefono), la misma del frontend: exactamente 10 dígitos, sin espacios ni símbolos. Se
 * prueba sobre los mensajes que la usan: registro, creación de usuario, perfil y titular de la reserva (también el del
 * canal externo).
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
    @ValueSource(strings = {"3001234567", "6041234567", "0000000000"})
    @DisplayName("Acepta teléfonos de exactamente 10 dígitos")
    void aceptaTelefonosValidos(String telefono) {
        assertTrue(erroresDeTelefono(telefono).isEmpty(), "Se rechazó " + telefono + ": " + erroresDeTelefono(telefono));
    }

    @ParameterizedTest(name = "\"{0}\" se rechaza")
    @ValueSource(strings = {"300123456", "30012345678", "1234567", "+573001234567", "300 123 4567", "300-123-4567",
            "(604)1234567", "300.123.4567", "300ABC4567", "+3001234567", " 3001234567", "3001234567 "})
    @DisplayName("Rechaza teléfonos con otra longitud, letras, espacios o símbolos")
    void rechazaTelefonosInvalidos(String telefono) {
        assertEquals(List.of(ReglaTelefono.MENSAJE), erroresDeTelefono(telefono));
    }

    @Test
    @DisplayName("El mensaje es el mismo del frontend")
    void mensaje() {
        assertEquals("El teléfono debe tener 10 dígitos, solo números", ReglaTelefono.MENSAJE);
    }

    @Test
    @DisplayName("Vacío o solo espacios: únicamente el mensaje de obligatorio")
    void vacioSoloTieneElMensajeDeObligatorio() {
        assertEquals(List.of("El teléfono es obligatorio"), erroresDeTelefono(""));
        assertEquals(List.of("El teléfono es obligatorio"), erroresDeTelefono("   "));
    }

    @Test
    @DisplayName("La misma regla rige en la creación de usuarios, el perfil y el titular de la reserva")
    void laMismaReglaEnTodosLosMensajes() {
        String malo = "300-123-4567";
        assertEquals(Set.of(ReglaTelefono.MENSAJE), mensajes(validador.validate(new CrearUsuarioRequest("Ana Torres",
                "ana@correo.co", "Segura2026", malo, "1095000001", LocalDate.of(1990, 5, 20), "HUESPED"))));
        assertEquals(Set.of(ReglaTelefono.MENSAJE),
                mensajes(validador.validate(new ActualizarPerfilRequest("Ana Torres", malo, null))));
        assertEquals(Set.of(ReglaTelefono.MENSAJE),
                mensajes(validador.validate(new TitularRequest("Ana Torres", "CC", "1095000001", "ana@correo.co", malo))));
        assertTrue(validador.validate(new TitularRequest("Ana Torres", "CC", "1095000001", "ana@correo.co",
                "3001234567")).isEmpty());
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

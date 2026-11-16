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
 * Regla del número de documento (ReglaDocumento), la misma del frontend: entre 6 y 10 dígitos, sin espacios ni
 * símbolos. Se prueba sobre los mensajes que la usan: registro, creación de usuario, titular y ocupantes de la reserva
 * (también los del canal externo).
 */
class ReglaDocumentoTest {

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
    @ValueSource(strings = {"123456", "1234567", "10950000", "109500001", "1095000001"})
    @DisplayName("Acepta documentos de 6 a 10 dígitos")
    void aceptaDocumentosValidos(String documento) {
        assertTrue(erroresDeDocumento(documento).isEmpty(), "Se rechazó " + documento + ": " + erroresDeDocumento(documento));
    }

    @ParameterizedTest(name = "\"{0}\" se rechaza")
    @ValueSource(strings = {"12345", "10950000011", "PA123456", "1095-000", "1.095.000", "1095 0000", " 1095000",
            "1095000 ", "+109500"})
    @DisplayName("Rechaza documentos con otra longitud, letras, espacios o símbolos")
    void rechazaDocumentosInvalidos(String documento) {
        assertEquals(List.of(ReglaDocumento.MENSAJE), erroresDeDocumento(documento));
    }

    @Test
    @DisplayName("El mensaje es el mismo del frontend")
    void mensaje() {
        assertEquals("El documento debe tener entre 6 y 10 dígitos, solo números", ReglaDocumento.MENSAJE);
    }

    @Test
    @DisplayName("Vacío o solo espacios: únicamente el mensaje de obligatorio")
    void vacioSoloTieneElMensajeDeObligatorio() {
        assertEquals(List.of("El documento es obligatorio"), erroresDeDocumento(""));
        assertEquals(List.of("El documento es obligatorio"), erroresDeDocumento("   "));
    }

    @Test
    @DisplayName("La misma regla rige en la creación de usuarios, el titular y los ocupantes de la reserva")
    void laMismaReglaEnTodosLosMensajes() {
        String malo = "CC-12345";
        assertEquals(Set.of(ReglaDocumento.MENSAJE), mensajes(validador.validate(new CrearUsuarioRequest("Ana Torres",
                "ana@correo.co", "Segura2026", "3001234567", malo, LocalDate.of(1990, 5, 20), "HUESPED"))));
        assertEquals(Set.of(ReglaDocumento.MENSAJE), mensajes(validador.validate(
                new TitularRequest("Ana Torres", "CC", malo, "ana@correo.co", "3001234567"))));
        assertEquals(Set.of(ReglaDocumento.MENSAJE), mensajes(validador.validate(
                new OcupanteRequest("Ana Torres", "CC", malo, LocalDate.of(1990, 5, 20)))));
    }

    @Test
    @DisplayName("En el ocupante el documento es opcional: sin él no hay error")
    void ocupanteSinDocumento() {
        assertTrue(validador.validate(new OcupanteRequest("Tomás Torres", null, null, LocalDate.of(2020, 1, 5)))
                .isEmpty());
        assertTrue(validador.validate(new OcupanteRequest("Tomás Torres", "", "", LocalDate.of(2020, 1, 5)))
                .isEmpty());
        assertTrue(validador.validate(new OcupanteRequest("Tomás Torres", "TI", "1095000002", LocalDate.of(2020, 1, 5)))
                .isEmpty());
    }

    /** Mensajes de error del documento en un registro con los demás datos válidos */
    private static List<String> erroresDeDocumento(String documento) {
        RegistroUsuarioRequest registro = new RegistroUsuarioRequest("Ana Torres", "ana@correo.co", "Segura2026",
                "3001234567", documento, LocalDate.of(1990, 5, 20));
        return validador.validate(registro).stream()
                .filter(v -> v.getPropertyPath().toString().equals("documento"))
                .map(ConstraintViolation::getMessage)
                .sorted()
                .toList();
    }

    private static Set<String> mensajes(Set<? extends ConstraintViolation<?>> violaciones) {
        return violaciones.stream().map(ConstraintViolation::getMessage).collect(Collectors.toSet());
    }
}

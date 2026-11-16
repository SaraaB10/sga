package co.edu.uniquindio.sga.application.dto.request;

import co.edu.uniquindio.sga.application.dto.validation.FechaNacimientoRazonable;
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
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reglas de forma de los mensajes de la API, las mismas del frontend: nombre de persona, correo, URL de imagen,
 * fecha de nacimiento razonable y textos libres con largo máximo y sin quedar solo en espacios.
 */
class ValidacionesLogicasTest {

    private static final LocalDate HOY = LocalDate.now(ZoneId.of("America/Bogota"));

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

    // ---------- Nombre de persona ----------

    @ParameterizedTest(name = "\"{0}\" es válido")
    @ValueSource(strings = {"Ana", "Ana Torres", "José Ñáñez", "María  del Mar", "Ángela Úrsula Güiza",
            "Aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"})
    @DisplayName("Nombre: letras con tildes y ñ, y espacios, de 3 a 80 caracteres")
    void aceptaNombresValidos(String nombre) {
        assertTrue(errores(registroConNombre(nombre), "nombre").isEmpty(), nombre);
    }

    @ParameterizedTest(name = "\"{0}\" se rechaza")
    @ValueSource(strings = {"Al", " Ana Torres", "Ana Torres ", "Ana3", "Ana-Torres", "Ana.Torres", "O'Neil",
            "Aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"})
    @DisplayName("Nombre: rechaza dígitos, símbolos, espacios a los lados y largos fuera de 3 a 80")
    void rechazaNombresInvalidos(String nombre) {
        assertEquals(List.of(ReglaNombre.MENSAJE), errores(registroConNombre(nombre), "nombre"));
    }

    @Test
    @DisplayName("Nombre: la misma regla en el perfil, el titular, los ocupantes, la novedad y el cierre del folio")
    void nombreEnTodosLosMensajes() {
        String malo = "Ana 2";
        assertEquals(List.of(ReglaNombre.MENSAJE),
                errores(new ActualizarPerfilRequest(malo, "3001234567", null), "nombre"));
        assertEquals(List.of(ReglaNombre.MENSAJE), errores(new TitularRequest(malo, "CC", "1095000001",
                "ana@correo.co", "3001234567"), "nombre"));
        assertEquals(List.of(ReglaNombre.MENSAJE),
                errores(new OcupanteRequest(malo, null, null, LocalDate.of(1990, 5, 20)), "nombre"));
        assertEquals(List.of(ReglaNombre.MENSAJE), errores(new RegistrarNovedadRequest(malo, "Fuga en el baño",
                null), "autor"));
        assertEquals(List.of(ReglaNombre.MENSAJE),
                errores(new AutorizarCierreFolioRequest(malo, "Saldo a favor"), "autorizadoPor"));
    }

    @Test
    @DisplayName("Nombre vacío: solo el mensaje de obligatorio")
    void nombreVacio() {
        assertEquals(List.of("El nombre es obligatorio"), errores(registroConNombre("   "), "nombre"));
    }

    // ---------- Correo ----------

    @ParameterizedTest(name = "\"{0}\" se rechaza")
    @ValueSource(strings = {"ana", "ana@", "ana@correo", "@correo.co", "ana torres@correo.co", "ana@correo..co"})
    @DisplayName("Correo: exige usuario, dominio y extensión")
    void rechazaCorreosInvalidos(String correo) {
        RegistroUsuarioRequest registro = new RegistroUsuarioRequest("Ana Torres", correo, "Segura2026",
                "3001234567", "1095000001", LocalDate.of(1990, 5, 20));
        assertEquals(List.of("El correo no tiene un formato válido"), errores(registro, "correo"));
    }

    @Test
    @DisplayName("Correo válido y el del titular con su propio mensaje")
    void correos() {
        assertTrue(errores(new LoginRequest("ana.torres+sga@correo.com.co", "x"), "correo").isEmpty());
        assertEquals(List.of("El email del titular no tiene un formato válido"), errores(new TitularRequest(
                "Ana Torres", "CC", "1095000001", "ana@correo", "3001234567"), "email"));
    }

    // ---------- URL de imagen ----------

    @Test
    @DisplayName("URL de imagen: solo https:// y sin espacios, en la foto del perfil y en las imágenes del apartamento")
    void urlDeImagen() {
        assertTrue(errores(new ImagenRequest("https://coral.co/a.jpg", true), "url").isEmpty());
        assertTrue(errores(new ActualizarPerfilRequest("Ana Torres", "3001234567", null), "fotoUrl").isEmpty());
        for (String url : List.of("http://coral.co/a.jpg", "ftp://coral.co/a.jpg", "coral.co/a.jpg",
                "https://coral.co/una foto.jpg", "https://")) {
            assertEquals(List.of(ReglaTexto.MENSAJE_URL_IMAGEN), errores(new ImagenRequest(url, true), "url"), url);
            assertEquals(List.of(ReglaTexto.MENSAJE_URL_IMAGEN),
                    errores(new ActualizarPerfilRequest("Ana Torres", "3001234567", url), "fotoUrl"), url);
        }
    }

    // ---------- Fecha de nacimiento ----------

    @Test
    @DisplayName("Fecha de nacimiento: hasta 110 años atrás sí; más atrás no, en cuentas y ocupantes")
    void fechaDeNacimientoRazonable() {
        LocalDate limite = HOY.minusYears(110);
        assertTrue(errores(registroConNacimiento(limite), "fechaNacimiento").isEmpty());
        assertEquals(List.of(FechaNacimientoRazonable.MENSAJE),
                errores(registroConNacimiento(limite.minusDays(1)), "fechaNacimiento"));
        assertEquals(List.of(FechaNacimientoRazonable.MENSAJE), errores(new OcupanteRequest("Ana Torres", null, null,
                LocalDate.of(1900, 1, 1)), "fechaNacimiento"));
        assertEquals(List.of(FechaNacimientoRazonable.MENSAJE), errores(new CrearUsuarioRequest("Ana Torres",
                "ana@correo.co", "Segura2026", "3001234567", "1095000001", LocalDate.of(1900, 1, 1), "HUESPED"),
                "fechaNacimiento"));
    }

    @Test
    @DisplayName("Fecha de nacimiento futura: la rechaza la cuenta y el ocupante")
    void fechaDeNacimientoFutura() {
        assertEquals(List.of("La fecha de nacimiento debe ser pasada"),
                errores(registroConNacimiento(HOY.plusDays(1)), "fechaNacimiento"));
        assertEquals(List.of("La fecha de nacimiento no puede ser futura"),
                errores(new OcupanteRequest("Ana Torres", null, null, HOY.plusDays(1)), "fechaNacimiento"));
        assertTrue(errores(new OcupanteRequest("Ana Torres", null, null, HOY), "fechaNacimiento").isEmpty());
    }

    // ---------- Textos libres ----------

    @Test
    @DisplayName("Textos libres: largo máximo y sin quedar solo en espacios")
    void textosLibres() {
        String largo = "a".repeat(ReglaTexto.MAXIMO_MOTIVO + 1);
        assertEquals(List.of("El motivo admite máximo 250 caracteres"),
                errores(new CancelarReservaRequest(largo), "motivo"));
        assertEquals(List.of("El motivo de la cancelación es obligatorio"),
                errores(new CancelarReservaRequest("   "), "motivo"));
        assertTrue(errores(new CancelarReservaRequest("a".repeat(ReglaTexto.MAXIMO_MOTIVO)), "motivo").isEmpty());

        ActualizarApartamentoRequest soloEspacios = new ActualizarApartamentoRequest("Suite", "   ", 1, 2,
                List.of("Cama doble"), null);
        assertEquals(List.of("La descripción no puede quedar solo en espacios"), errores(soloEspacios, "descripcion"));
        ActualizarApartamentoRequest sinDescripcion = new ActualizarApartamentoRequest("Suite", "", 1, 2,
                List.of("Cama doble"), null);
        assertTrue(errores(sinDescripcion, "descripcion").isEmpty());
        ActualizarApartamentoRequest larga = new ActualizarApartamentoRequest("Suite", "a".repeat(1001), 1, 2,
                List.of("Cama doble"), null);
        assertEquals(List.of("La descripción admite máximo 1000 caracteres"), errores(larga, "descripcion"));
    }

    @Test
    @DisplayName("Características del apartamento: opcionales, sin vacías, de 2 a 60 caracteres y máximo 20")
    void caracteristicasDelApartamento() {
        ActualizarApartamentoRequest valida = new ActualizarApartamentoRequest("Suite", "", 1, 2,
                List.of("Cama doble"), List.of("Vista al mar", "Balcón"));
        assertTrue(validador.validate(valida).isEmpty());
        ActualizarApartamentoRequest vacia = new ActualizarApartamentoRequest("Suite", "", 1, 2,
                List.of("Cama doble"), List.of(" "));
        assertTrue(validador.validate(vacia).stream()
                .anyMatch(v -> v.getMessage().equals("Las características no pueden estar vacías")));
        ActualizarApartamentoRequest larga = new ActualizarApartamentoRequest("Suite", "", 1, 2,
                List.of("Cama doble"), List.of("x".repeat(61)));
        assertTrue(validador.validate(larga).stream()
                .anyMatch(v -> v.getMessage().equals("Cada característica debe tener entre 2 y 60 caracteres")));
        ActualizarApartamentoRequest muchas = new ActualizarApartamentoRequest("Suite", "", 1, 2,
                List.of("Cama doble"), java.util.stream.IntStream.range(0, 21).mapToObj(i -> "Rasgo " + i).toList());
        assertEquals(List.of("El apartamento admite máximo 20 características"), errores(muchas, "caracteristicas"));
    }

    @Test
    @DisplayName("La posición del cargo a corregir no es negativa")
    void posicionDelCargo() {
        assertEquals(List.of("La posición del cargo no puede ser negativa"),
                errores(new RegistrarCorreccionRequest(-1, "Error de digitación"), "indiceCargo"));
        assertTrue(errores(new RegistrarCorreccionRequest(0, "Error de digitación"), "indiceCargo").isEmpty());
    }

    private static RegistroUsuarioRequest registroConNombre(String nombre) {
        return new RegistroUsuarioRequest(nombre, "ana@correo.co", "Segura2026", "3001234567", "1095000001",
                LocalDate.of(1990, 5, 20));
    }

    private static RegistroUsuarioRequest registroConNacimiento(LocalDate fecha) {
        return new RegistroUsuarioRequest("Ana Torres", "ana@correo.co", "Segura2026", "3001234567", "1095000001",
                fecha);
    }

    /** Mensajes de error de un campo, ordenados */
    private static List<String> errores(Object objeto, String campo) {
        return validador.validate(objeto).stream()
                .filter(v -> v.getPropertyPath().toString().equals(campo))
                .map(ConstraintViolation::getMessage)
                .sorted()
                .toList();
    }
}

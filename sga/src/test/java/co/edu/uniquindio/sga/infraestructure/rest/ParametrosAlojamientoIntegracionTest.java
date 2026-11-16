package co.edu.uniquindio.sga.infraestructure.rest;

import co.edu.uniquindio.sga.infraestructure.config.SeguridadPruebasConfig;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Parámetros del alojamiento guardados en la base y editados por el administrador (PUT /api/alojamiento/parametros):
 * nacen con los valores de application.properties y, al cambiarlos, rigen en la siguiente operación sin reiniciar.
 * Usa su propia base H2 y deja el contexto sucio, para que las demás pruebas sigan viendo los parámetros iniciales.
 */
@Import(SeguridadPruebasConfig.class)
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:sga-parametros")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ParametrosAlojamientoIntegracionTest {

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");

    @Autowired
    private MockMvc mvc;

    @Test
    @DisplayName("Los parámetros nacen con los valores de application.properties, incluidos depósito, recargo y edad")
    void valoresIniciales() throws Exception {
        mvc.perform(MockMvcRequestBuilders.get("/api/alojamiento"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parametros.umbralEdadFacturable").value(6))
                .andExpect(jsonPath("$.parametros.depositoGarantia").value(250000))
                .andExpect(jsonPath("$.parametros.conceptoDepositoGarantia", startsWith("Depósito de garantía")))
                .andExpect(jsonPath("$.parametros.recargoRecepcionNocturna").value(70000))
                .andExpect(jsonPath("$.parametros.horaInicioLlegadaNocturna", startsWith("20:00")))
                .andExpect(jsonPath("$.parametros.edadMinimaCuenta").value(18));
    }

    @Test
    @DisplayName("Cambiar el umbral de edad facturable cambia la cotización de inmediato, sin reiniciar")
    void elUmbralCambiaLaCotizacion() throws Exception {
        String cotizacion = cotizacionDeAdultoYNinoDe8();
        Number antes = JsonPath.read(cotizar(cotizacion).andExpect(status().isOk())
                .andExpect(jsonPath("$.detalles[0].ocupantesFacturables").value(2))
                .andReturn().getResponse().getContentAsString(), "$.valorTotal");

        // Con umbral 10, el niño de 8 años deja de pagar noche
        actualizar(parametros(Map.of("umbralEdadFacturable", 10))).andExpect(status().isOk())
                .andExpect(jsonPath("$.parametros.umbralEdadFacturable").value(10));

        Number despues = JsonPath.read(cotizar(cotizacion).andExpect(status().isOk())
                .andExpect(jsonPath("$.detalles[0].ocupantesFacturables").value(1))
                .andReturn().getResponse().getContentAsString(), "$.valorTotal");
        assertEquals(antes.longValue() / 2, despues.longValue(), "Debía cobrar a una sola persona por noche");
    }

    @Test
    @DisplayName("Depósito, recargo nocturno, anticipo y edad mínima se guardan y se devuelven en GET /api/alojamiento")
    void seGuardanTodosLosParametros() throws Exception {
        Map<String, Object> cambios = new LinkedHashMap<>();
        cambios.put("depositoGarantia", 300000);
        cambios.put("conceptoDepositoGarantia", "Depósito por dotación");
        cambios.put("recargoRecepcionNocturna", 80000);
        cambios.put("horaInicioLlegadaNocturna", "21:00");
        cambios.put("porcentajeAnticipo", 40);
        cambios.put("edadMinimaCuenta", 21);
        actualizar(parametros(cambios)).andExpect(status().isOk());

        mvc.perform(MockMvcRequestBuilders.get("/api/alojamiento"))
                .andExpect(jsonPath("$.parametros.depositoGarantia").value(300000))
                .andExpect(jsonPath("$.parametros.conceptoDepositoGarantia").value("Depósito por dotación"))
                .andExpect(jsonPath("$.parametros.recargoRecepcionNocturna").value(80000))
                .andExpect(jsonPath("$.parametros.horaInicioLlegadaNocturna", startsWith("21:00")))
                .andExpect(jsonPath("$.parametros.porcentajeAnticipo").value(40))
                .andExpect(jsonPath("$.parametros.edadMinimaCuenta").value(21));
    }

    @Test
    @DisplayName("La edad mínima de la cuenta nueva rige en el registro de inmediato")
    void laEdadMinimaRigeEnElRegistro() throws Exception {
        actualizar(parametros(Map.of("edadMinimaCuenta", 21))).andExpect(status().isOk());

        LocalDate nacio19 = LocalDate.now(BOGOTA).minusYears(19);
        mvc.perform(MockMvcRequestBuilders.post("/api/auth/registro").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"Ana Torres","correo":"ana.parametros@correo.co","contrasena":"Segura2026",
                                 "telefono":"3160000000","documento":"1095009001","fechaNacimiento":"%s"}
                                """.formatted(nacio19)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.fechaNacimiento")
                        .value("Debes tener al menos 21 años para crear una cuenta"));
    }

    @Test
    @DisplayName("400 con un parámetro fuera de rango y 409 si se exige anticipo con 0 %; nada cambia")
    void validaLosParametros() throws Exception {
        actualizar(parametros(Map.of("umbralEdadFacturable", 30)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.umbralEdadFacturable")
                        .value("El umbral de edad facturable debe estar entre 0 y 17 años"));
        actualizar(parametros(Map.of("recargoRecepcionNocturna", 0)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.recargoRecepcionNocturna")
                        .value("El recargo de recepción nocturna debe ser mayor que cero"));
        actualizar(parametros(Map.of("porcentajeAnticipo", 0)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje", startsWith("Si se exige anticipo")));

        mvc.perform(MockMvcRequestBuilders.get("/api/alojamiento"))
                .andExpect(jsonPath("$.parametros.umbralEdadFacturable").value(6))
                .andExpect(jsonPath("$.parametros.porcentajeAnticipo").value(50))
                .andExpect(jsonPath("$.parametros.recargoRecepcionNocturna").value(70000));
    }

    @Test
    @DisplayName("Solo el administrador cambia los parámetros: la recepción recibe 403")
    void soloElAdministrador() throws Exception {
        mvc.perform(MockMvcRequestBuilders.put("/api/alojamiento/parametros")
                        .with(jwt().jwt(t -> t.subject("recepcion@sga.local"))
                                .authorities(new SimpleGrantedAuthority("RECEPCIONISTA")))
                        .contentType(MediaType.APPLICATION_JSON).content(parametros(Map.of())))
                .andExpect(status().isForbidden());
    }

    /** Cotización de lunes a miércoles en APT-201: un adulto y un niño que tiene 8 años a la entrada */
    private static String cotizacionDeAdultoYNinoDe8() {
        LocalDate entrada = LocalDate.now(BOGOTA).plusDays(60);
        while (entrada.getDayOfWeek() != DayOfWeek.MONDAY) {
            entrada = entrada.plusDays(1);
        }
        LocalDate nino = entrada.minusYears(8).minusDays(10);
        assertTrue(nino.isBefore(entrada));
        return """
                {"identificacionApartamento":"APT-201","fechaEntrada":"%s","fechaSalida":"%s",
                 "ocupantes":[{"nombre":"Ana Torres","fechaNacimiento":"1990-05-20"},
                              {"nombre":"Tomas Torres","fechaNacimiento":"%s"}]}
                """.formatted(entrada, entrada.plusDays(2), nino);
    }

    /** Los parámetros iniciales con los cambios indicados, como JSON */
    private static String parametros(Map<String, Object> cambios) {
        Map<String, Object> valores = new LinkedHashMap<>();
        valores.put("umbralEdadFacturable", 6);
        valores.put("tiempoPreparacionHoras", 3);
        valores.put("plazoConfirmacionHoras", 12);
        valores.put("horaLimiteNoShow", "22:00");
        valores.put("horaInicioLlegadaNocturna", "20:00");
        valores.put("recargoRecepcionNocturna", 70000);
        valores.put("estanciaMinimaNoches", 2);
        valores.put("estanciaMinimaActiva", true);
        valores.put("exigeAnticipo", true);
        valores.put("porcentajeAnticipo", 50);
        valores.put("conceptoDepositoGarantia", "Depósito de garantía por inventario y cristalería fina");
        valores.put("depositoGarantia", 250000);
        valores.put("aceptaMascotas", true);
        valores.put("cargoAseoMascota", 110000);
        valores.put("maximoMascotas", 2);
        valores.put("pesoMaximoMascotaKg", 10);
        valores.put("edadMinimaCuenta", 18);
        valores.putAll(cambios);
        return valores.entrySet().stream()
                .map(e -> "\"" + e.getKey() + "\":" + (e.getValue() instanceof String s ? "\"" + s + "\"" : e.getValue()))
                .collect(Collectors.joining(",", "{", "}"));
    }

    private ResultActions actualizar(String json) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.put("/api/alojamiento/parametros")
                .with(jwt().jwt(t -> t.subject("admin@sga.local"))
                        .authorities(new SimpleGrantedAuthority("ADMINISTRADOR")))
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions cotizar(String json) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.post("/api/cotizaciones")
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }
}

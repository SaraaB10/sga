package co.edu.uniquindio.sga.infraestructure.rest;

import co.edu.uniquindio.sga.infraestructure.config.SeguridadPruebasConfig;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Catálogo de medios de pago aceptados (L-18): nace con los de application.properties, el administrador agrega,
 * activa y desactiva medios, y solo los activos se publican y se aceptan al registrar un pago.
 */
@Import(SeguridadPruebasConfig.class)
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:sga-medios-pago")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class MedioPagoIntegracionTest {

    @Autowired
    private MockMvc mvc;

    @Test
    @DisplayName("GET público: los dos medios iniciales, activos y en orden")
    void mediosIniciales() throws Exception {
        mvc.perform(MockMvcRequestBuilders.get("/api/medios-pago"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].codigo", contains("TARJETA_DEBITO_CREDITO", "TRANSFERENCIA_BANCARIA")))
                .andExpect(jsonPath("$[0].nombre").value("Tarjeta débito o crédito"))
                .andExpect(jsonPath("$[1].activo").value(true));
    }

    @Test
    @DisplayName("El administrador agrega un medio (código tomado del nombre); repetido 409")
    void agregarMedio() throws Exception {
        como("ADMINISTRADOR", MockMvcRequestBuilders.post("/api/medios-pago"), "{\"nombre\":\"Efectivo en pesos\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.codigo").value("EFECTIVO_EN_PESOS"))
                .andExpect(jsonPath("$.activo").value(true));
        mvc.perform(MockMvcRequestBuilders.get("/api/medios-pago"))
                .andExpect(jsonPath("$[*].codigo", hasItem("EFECTIVO_EN_PESOS")));

        como("ADMINISTRADOR", MockMvcRequestBuilders.post("/api/medios-pago"), "{\"nombre\":\"efectivo en pesos\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje").value("L-18: ya existe el medio de pago EFECTIVO_EN_PESOS"));
        como("ADMINISTRADOR", MockMvcRequestBuilders.post("/api/medios-pago"), "{\"nombre\":\"  \"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.nombre").value("El nombre del medio de pago es obligatorio"));
    }

    @Test
    @DisplayName("Desactivar lo quita de la lista pública; activar lo devuelve; el último activo no se desactiva")
    void activarYDesactivar() throws Exception {
        como("ADMINISTRADOR", MockMvcRequestBuilders.put("/api/medios-pago/TARJETA_DEBITO_CREDITO/desactivar"), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activo").value(false));
        mvc.perform(MockMvcRequestBuilders.get("/api/medios-pago"))
                .andExpect(jsonPath("$[*].codigo", not(hasItem("TARJETA_DEBITO_CREDITO"))));
        como("ADMINISTRADOR", MockMvcRequestBuilders.get("/api/medios-pago/todos"), null)
                .andExpect(jsonPath("$[*].codigo", hasItem("TARJETA_DEBITO_CREDITO")));

        como("ADMINISTRADOR", MockMvcRequestBuilders.put("/api/medios-pago/TRANSFERENCIA_BANCARIA/desactivar"), null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje").value("L-18: debe quedar al menos un medio de pago activo"));

        como("ADMINISTRADOR", MockMvcRequestBuilders.put("/api/medios-pago/TARJETA_DEBITO_CREDITO/activar"), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activo").value(true));
        como("ADMINISTRADOR", MockMvcRequestBuilders.put("/api/medios-pago/TARJETA_DEBITO_CREDITO/activar"), null)
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("404 si el medio no existe y 403 si no es el administrador")
    void errores() throws Exception {
        como("ADMINISTRADOR", MockMvcRequestBuilders.put("/api/medios-pago/CHEQUE/desactivar"), null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensaje").value("No se encontró el medio de pago con código: CHEQUE"));
        como("RECEPCIONISTA", MockMvcRequestBuilders.post("/api/medios-pago"), "{\"nombre\":\"Efectivo\"}")
                .andExpect(status().isForbidden());
        como("RECEPCIONISTA", MockMvcRequestBuilders.get("/api/medios-pago/todos"), null)
                .andExpect(status().isForbidden());
    }

    private ResultActions como(String rol, MockHttpServletRequestBuilder peticion, String json) throws Exception {
        peticion.with(jwt().jwt(t -> t.subject("usuario@sga.local")).authorities(new SimpleGrantedAuthority(rol)));
        if (json != null) {
            peticion.contentType(MediaType.APPLICATION_JSON).content(json);
        }
        return mvc.perform(peticion);
    }
}

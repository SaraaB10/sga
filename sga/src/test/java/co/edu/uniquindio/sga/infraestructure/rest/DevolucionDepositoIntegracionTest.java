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
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * RP-02 con la salida: la reserva EN_CURSO de los datos iniciales paga el saldo, recibe de vuelta el depósito, cierra
 * el folio en cero y sale. Usa su propia base en memoria porque la saca de EN_CURSO y otras pruebas la necesitan.
 */
@Import(SeguridadPruebasConfig.class)
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:sga-devolucion-deposito")
@AutoConfigureMockMvc
@DirtiesContext
class DevolucionDepositoIntegracionTest {

    private static final long VALOR_DEPOSITO_GARANTIA = 250_000;
    private static final String MEDIO = "{\"medio\":\"TARJETA_DEBITO_CREDITO\"}";

    @Autowired
    private MockMvc mvc;

    @Test
    @DisplayName("RP-02: devolver el depósito asienta su inverso y la devolución, el folio cierra en cero y la reserva "
            + "sale")
    void devolverDepositoYSalir() throws Exception {
        // La reserva EN_CURSO de los datos iniciales: pagó solo el anticipo
        String codigo = JsonPath.read(get("/api/reservas?estado=EN_CURSO").andReturn().getResponse()
                .getContentAsString(), "$.contenido[0].codigo");
        String folio = "/api/reservas/" + codigo + "/folio";
        long saldo = numero(get(folio), "$.saldo");
        post(folio + "/pagos", "{\"valor\":" + saldo + ",\"medio\":\"TARJETA_DEBITO_CREDITO\"}")
                .andExpect(jsonPath("$.saldo").value(0));

        ResultActions devuelto = post(folio + "/devolucion-deposito", MEDIO)
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith(folio)))
                .andExpect(jsonPath("$.depositoReembolsado").value(true))
                .andExpect(jsonPath("$.cargos[2].valor").value(-VALOR_DEPOSITO_GARANTIA))
                .andExpect(jsonPath("$.pagos[2].devolucion").value(true))
                .andExpect(jsonPath("$.pagos[2].valor").value(VALOR_DEPOSITO_GARANTIA))
                .andExpect(jsonPath("$.saldo").value(0));
        // La devolución resta del total pagado
        assertEquals(numero(devuelto, "$.totalCargos"), numero(devuelto, "$.totalPagado"));

        // Una sola vez por folio
        post(folio + "/devolucion-deposito", MEDIO)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje", startsWith("RP-02")));

        mvc.perform(MockMvcRequestBuilders.put("/api/reservas/" + codigo + "/registrar-salida"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("FINALIZADA"));
        get(folio).andExpect(jsonPath("$.cerrado").value(true));

        // Ya salió: el depósito no se devuelve fuera de la estancia
        post(folio + "/devolucion-deposito", MEDIO).andExpect(status().isConflict());
    }

    private ResultActions get(String url) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.get(url));
    }

    private ResultActions post(String url, String json) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.post(url).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private static long numero(ResultActions respuesta, String ruta) throws Exception {
        Number valor = JsonPath.read(respuesta.andReturn().getResponse().getContentAsString(), ruta);
        return valor.longValue();
    }
}

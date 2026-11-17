package co.edu.uniquindio.sga.infraestructure.rest;

import co.edu.uniquindio.sga.infraestructure.config.SeguridadPruebasConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.Map;

import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integración de la gestión de servicios adicionales del alojamiento: crear, consultar, editar, desactivar y volver
 * a activar, con las protecciones de RP-03 sobre la recepción nocturna (SRV-RECEPCION-NOCTURNA en los datos iniciales).
 * Usa su propia base H2 para no cambiar los servicios que ven las demás pruebas.
 */
@Import(SeguridadPruebasConfig.class)
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:sga-servicios")
@AutoConfigureMockMvc
class AlojamientoServiciosIntegracionTest {

    private static final String NOCTURNA = "/api/alojamiento/servicios/SRV-RECEPCION-NOCTURNA";

    @Autowired
    private MockMvc mvc;

    @Test
    @DisplayName("El administrador crea, edita, desactiva y reactiva un servicio adicional")
    void cicloDeUnServicio() throws Exception {
        String location = post("/api/alojamiento/servicios", """
                {"id":"srv-spa","nombre":"Spa en la habitación","descripcion":"Masaje de 60 minutos",
                 "generaCargo":true,"valor":180000}""")
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith("/api/alojamiento/servicios/SRV-SPA")))
                .andExpect(jsonPath("$.id").value("SRV-SPA"))
                .andExpect(jsonPath("$.valor").value(180000))
                .andExpect(jsonPath("$.activo").value(true))
                .andExpect(jsonPath("$.motivoObligatorio").value(nullValue()))
                .andReturn().getResponse().getHeader("Location");

        get(location).andExpect(status().isOk()).andExpect(jsonPath("$.nombre").value("Spa en la habitación"));
        // Aparece en los datos públicos del alojamiento
        get("/api/alojamiento").andExpect(jsonPath("$.serviciosAdicionales[?(@.id=='SRV-SPA')].valor").value(180000));

        // Editar: pasa a ser sin cargo y el valor queda en cero
        put("/api/alojamiento/servicios/SRV-SPA", """
                {"nombre":"Spa de cortesía","descripcion":"Incluido en la estancia","generaCargo":false,"valor":0}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Spa de cortesía"))
                .andExpect(jsonPath("$.generaCargo").value(false))
                .andExpect(jsonPath("$.valor").value(0));
        put("/api/alojamiento/servicios/SRV-SPA", """
                {"nombre":"Spa en la habitación","generaCargo":true,"valor":200000}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valor").value(200000));

        put("/api/alojamiento/servicios/SRV-SPA/desactivar", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activo").value(false));
        put("/api/alojamiento/servicios/SRV-SPA/desactivar", null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje").value("El servicio Spa en la habitación ya está desactivado"));
        put("/api/alojamiento/servicios/SRV-SPA/activar", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activo").value(true));
    }

    @Test
    @DisplayName("RP-03: la recepción nocturna no se desactiva ni se deja sin cargo, pero su valor sí se edita")
    void recepcionNocturnaProtegida() throws Exception {
        get("/api/alojamiento")
                .andExpect(jsonPath("$.serviciosAdicionales[?(@.id=='SRV-RECEPCION-NOCTURNA')].motivoObligatorio",
                        org.hamcrest.Matchers.contains(startsWith("Se cobra automáticamente"))));

        put(NOCTURNA + "/desactivar", null)
                .andExpect(CuerpoError.error(HttpStatus.CONFLICT,
                        "RP-03: El servicio Recepción nocturna no se puede desactivar: cobra el recargo de las "
                                + "llegadas nocturnas", NOCTURNA + "/desactivar"));
        put(NOCTURNA, """
                {"nombre":"Recepción nocturna","generaCargo":false}""")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje", startsWith("RP-03")));
        put(NOCTURNA, """
                {"nombre":"Recepción nocturna","descripcion":"Llegadas desde las 20:00","generaCargo":true,
                 "valor":75000}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valor").value(75000))
                .andExpect(jsonPath("$.activo").value(true));
    }

    @Test
    @DisplayName("Errores: 400 forma, 409 reglas, 404 inexistente y 403 si no es administrador")
    void erroresDeServicios() throws Exception {
        post("/api/alojamiento/servicios", "{\"id\":\"SRV-X\",\"valor\":-5}")
                .andExpect(CuerpoError.validacion("/api/alojamiento/servicios", Map.of(
                        "nombre", "El nombre del servicio es obligatorio",
                        "generaCargo", "Indique si el servicio genera cargo",
                        "valor", "El valor no puede ser negativo")));
        post("/api/alojamiento/servicios", """
                {"id":"SRV-PASEO-BAHIA","nombre":"Otro paseo","generaCargo":true,"valor":1000}""")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje").value("Ya existe el servicio adicional SRV-PASEO-BAHIA"));
        post("/api/alojamiento/servicios", """
                {"id":"SRV-GRATIS","nombre":"Con cargo sin valor","generaCargo":true,"valor":0}""")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje").value("Un servicio que genera cargo debe tener un valor mayor que cero"));
        post("/api/alojamiento/servicios", """
                {"id":"SRV ESPACIO","nombre":"Mal id","generaCargo":false}""")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje", startsWith("El id del servicio solo admite")));

        get("/api/alojamiento/servicios/SRV-NO-EXISTE")
                .andExpect(CuerpoError.error(HttpStatus.NOT_FOUND,
                        "No se encontró el servicio adicional con id: SRV-NO-EXISTE",
                        "/api/alojamiento/servicios/SRV-NO-EXISTE"));
        put("/api/alojamiento/servicios/SRV-NO-EXISTE/desactivar", null).andExpect(status().isNotFound());

        mvc.perform(MockMvcRequestBuilders.put("/api/alojamiento/servicios/SRV-PASEO-BAHIA/desactivar")
                        .with(jwt().authorities(new SimpleGrantedAuthority("RECEPCIONISTA"))))
                .andExpect(CuerpoError.error(HttpStatus.FORBIDDEN, CuerpoError.MENSAJE_403,
                        "/api/alojamiento/servicios/SRV-PASEO-BAHIA/desactivar"));
    }

    private ResultActions get(String url) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.get(url));
    }

    private ResultActions post(String url, String json) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.post(url).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions put(String url, String json) throws Exception {
        var peticion = MockMvcRequestBuilders.put(url);
        if (json != null) peticion.contentType(MediaType.APPLICATION_JSON).content(json);
        return mvc.perform(peticion);
    }
}

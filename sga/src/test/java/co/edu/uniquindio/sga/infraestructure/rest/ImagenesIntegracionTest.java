package co.edu.uniquindio.sga.infraestructure.rest;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.nio.charset.StandardCharsets;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Subida de fotos con el almacén local (el de las pruebas, en build/imagenes-pruebas): el administrador sube una
 * foto, el backend la sirve sin token y la URL se usa al registrar un apartamento. Usa su propia base H2.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:sga-imagenes")
@AutoConfigureMockMvc
class ImagenesIntegracionTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0, 0, 13, 'I', 'H', 'D', 'R'};

    @Autowired
    private MockMvc mvc;

    @Test
    @DisplayName("El administrador sube una foto, se sirve sin token y sirve para registrar un apartamento")
    void subeSirveYRegistra() throws Exception {
        String respuesta = subir("ADMINISTRADOR", new MockMultipartFile("archivo", "sala.png", "image/png", PNG))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.url", matchesPattern("^http://localhost:\\d+/api/imagenes/[0-9a-f-]{36}\\.png$")))
                .andReturn().getResponse().getContentAsString();
        String url = JsonPath.read(respuesta, "$.url");
        String ruta = url.substring(url.indexOf("/api/imagenes/"));

        mvc.perform(MockMvcRequestBuilders.get(ruta))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/png"))
                .andExpect(content().bytes(PNG));

        mvc.perform(MockMvcRequestBuilders.post("/api/apartamentos")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ADMINISTRADOR")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"identificacion":"APT-501","nombre":"Coral Foto","descripcion":null,
                                 "dormitorios":1,"capacidadMaxima":2,"dotacion":["Cocina equipada"],
                                 "imagenes":[{"url":"%s","principal":true},
                                             {"url":"https://coral.co/img/apt-501-balcon.jpg","principal":false}]}
                                """.formatted(url)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.imagenes[0].url").value(url))
                .andExpect(jsonPath("$.imagenes[0].principal").value(true));
    }

    @Test
    @DisplayName("Un archivo que no es foto o una petición sin archivo responden 400 con el campo archivo")
    void archivoInvalido() throws Exception {
        subir("ADMINISTRADOR", new MockMultipartFile("archivo", "foto.png", "image/png",
                "<script>alert(1)</script>".getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.archivo").value("La foto debe ser JPG, PNG o WebP"));
        subir("ADMINISTRADOR", new MockMultipartFile("otro", "foto.png", "image/png", PNG))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.archivo").value("Selecciona una foto"));
    }

    @Test
    @DisplayName("Solo el administrador sube fotos; sin token responde 401")
    void permisos() throws Exception {
        MockMultipartFile foto = new MockMultipartFile("archivo", "sala.png", "image/png", PNG);
        subir("RECEPCIONISTA", foto).andExpect(status().isForbidden());
        subir("HUESPED", foto).andExpect(status().isForbidden());
        mvc.perform(MockMvcRequestBuilders.multipart("/api/imagenes").file(foto))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Una foto que no existe o un nombre fuera de la carpeta responden 404")
    void fotoInexistente() throws Exception {
        mvc.perform(MockMvcRequestBuilders.get("/api/imagenes/00000000-0000-0000-0000-000000000000.png"))
                .andExpect(status().isNotFound());
        mvc.perform(MockMvcRequestBuilders.get("/api/imagenes/application.properties"))
                .andExpect(status().isNotFound());
    }

    private ResultActions subir(String rol, MockMultipartFile archivo) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.multipart("/api/imagenes").file(archivo)
                .with(jwt().authorities(new SimpleGrantedAuthority(rol))));
    }
}

package co.edu.uniquindio.sga.infrastructure.imagenes;

import co.edu.uniquindio.sga.domain.exception.ImagenNoAlmacenadaException;
import co.edu.uniquindio.sga.domain.port.AlmacenImagenes;
import co.edu.uniquindio.sga.domain.valueobject.FormatoImagen;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Adaptador del puerto AlmacenImagenes con Cloudinary (sga.imagenes.proveedor=cloudinary). Hace una subida
 * firmada a la API REST (POST {url}/{cloud-name}/image/upload): la firma es el SHA-1 de los parámetros ordenados
 * más el api-secret, así el secreto nunca viaja. Devuelve la secure_url (https) de la foto.
 * Las credenciales (sga.imagenes.cloudinary.cloud-name, api-key y api-secret) van solo en
 * application-secrets.properties o en variables de entorno. Con un error de red, una respuesta lenta
 * (sga.imagenes.cloudinary.tiempo-espera), un rechazo o sin credenciales, lanza ImagenNoAlmacenadaException.
 */
public class AlmacenImagenesCloudinary implements AlmacenImagenes {

    private static final Logger log = LoggerFactory.getLogger(AlmacenImagenesCloudinary.class);

    private final RestClient cliente;
    private final String cloudName;
    private final String apiKey;
    private final String apiSecret;
    private final String carpeta;
    private final Clock reloj;

    /**
     * @param url          base de la API, por ejemplo https://api.cloudinary.com/v1_1
     * @param carpeta      carpeta de Cloudinary donde quedan las fotos; vacía las deja en la raíz
     * @param tiempoEspera tiempo máximo para conectar y para recibir la respuesta
     */
    public AlmacenImagenesCloudinary(String url, String cloudName, String apiKey, String apiSecret, String carpeta,
                                     Duration tiempoEspera, Clock reloj) {
        this.cloudName = limpio(cloudName);
        this.apiKey = limpio(apiKey);
        this.apiSecret = limpio(apiSecret);
        this.carpeta = limpio(carpeta);
        this.reloj = Objects.requireNonNull(reloj);
        Objects.requireNonNull(tiempoEspera);
        JdkClientHttpRequestFactory fabrica = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(tiempoEspera).build());
        fabrica.setReadTimeout(tiempoEspera);
        this.cliente = RestClient.builder()
                .baseUrl(Objects.requireNonNull(url))
                .requestFactory(fabrica)
                .build();
        if (!tieneCredenciales()) {
            log.warn("Imágenes: faltan sga.imagenes.cloudinary.cloud-name, api-key o api-secret; las fotos no se "
                    + "van a poder subir a Cloudinary");
        }
    }

    @Override
    public String guardar(byte[] contenido, FormatoImagen formato) {
        if (!tieneCredenciales()) {
            throw new ImagenNoAlmacenadaException("Faltan las credenciales de Cloudinary; la foto no se subió");
        }
        String marcaTiempo = String.valueOf(reloj.instant().getEpochSecond());
        MultiValueMap<String, Object> partes = new LinkedMultiValueMap<>();
        partes.add("file", new ByteArrayResource(contenido) {
            @Override
            public String getFilename() {
                return "foto." + formato.extension();
            }
        });
        partes.add("api_key", apiKey);
        partes.add("timestamp", marcaTiempo);
        if (!carpeta.isEmpty()) {
            partes.add("folder", carpeta);
        }
        partes.add("signature", firma(carpeta, marcaTiempo, apiSecret));
        try {
            RespuestaCloudinary respuesta = cliente.post()
                    .uri("/{cloudName}/image/upload", cloudName)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(partes)
                    .retrieve()
                    .body(RespuestaCloudinary.class);
            if (respuesta == null || respuesta.secureUrl() == null || respuesta.secureUrl().isBlank()) {
                throw new ImagenNoAlmacenadaException("Cloudinary no devolvió la URL de la foto");
            }
            log.info("Imágenes: foto subida a Cloudinary ({})", respuesta.secureUrl());
            return respuesta.secureUrl();
        } catch (ImagenNoAlmacenadaException e) {
            log.warn("Imágenes: {}", e.getMessage());
            throw e;
        } catch (RuntimeException e) {
            log.warn("Imágenes: no se pudo subir la foto a Cloudinary ({}): {}", e.getClass().getSimpleName(),
                    e.getMessage());
            throw new ImagenNoAlmacenadaException("No se pudo subir la foto a Cloudinary", e);
        }
    }

    /**
     * Firma de la subida: SHA-1 en hexadecimal de los parámetros firmados en orden alfabético
     * (folder=...&timestamp=...) seguidos del api-secret.
     */
    static String firma(String carpeta, String marcaTiempo, String apiSecret) {
        String parametros = (carpeta == null || carpeta.isBlank() ? "" : "folder=" + carpeta + "&")
                + "timestamp=" + marcaTiempo;
        try {
            byte[] resumen = MessageDigest.getInstance("SHA-1")
                    .digest((parametros + apiSecret).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(resumen);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("La JVM no tiene SHA-1", e);
        }
    }

    private boolean tieneCredenciales() {
        return !cloudName.isEmpty() && !apiKey.isEmpty() && !apiSecret.isEmpty();
    }

    private static String limpio(String valor) {
        return valor == null ? "" : valor.trim();
    }

    /** Lo que se usa de la respuesta de Cloudinary; el nombre del campo es el de la API. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record RespuestaCloudinary(@JsonProperty("secure_url") String secureUrl) {
    }
}

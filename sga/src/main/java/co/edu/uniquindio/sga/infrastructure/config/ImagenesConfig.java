package co.edu.uniquindio.sga.infrastructure.config;

import co.edu.uniquindio.sga.domain.port.AlmacenImagenes;
import co.edu.uniquindio.sga.infrastructure.imagenes.AlmacenImagenesCloudinary;
import co.edu.uniquindio.sga.infrastructure.imagenes.AlmacenImagenesLocal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.Locale;

/**
 * Único lugar donde se elige el adaptador del puerto AlmacenImagenes, según sga.imagenes.proveedor: cloudinary
 * (servicio externo, subida firmada) o local (disco del servidor, el de por defecto y el de las pruebas). Un valor
 * desconocido se registra en el log y deja el local.
 */
@Configuration
public class ImagenesConfig {

    private static final Logger log = LoggerFactory.getLogger(ImagenesConfig.class);

    @Bean
    public AlmacenImagenes almacenImagenes(
            @Value("${sga.imagenes.proveedor:local}") String proveedor,
            @Value("${sga.imagenes.local.directorio:./data/imagenes}") String directorio,
            @Value("${sga.imagenes.local.url-publica:http://localhost:8080}") String urlPublica,
            @Value("${sga.imagenes.cloudinary.url:https://api.cloudinary.com/v1_1}") String urlCloudinary,
            @Value("${sga.imagenes.cloudinary.cloud-name:}") String cloudName,
            @Value("${sga.imagenes.cloudinary.api-key:}") String apiKey,
            @Value("${sga.imagenes.cloudinary.api-secret:}") String apiSecret,
            @Value("${sga.imagenes.cloudinary.carpeta:}") String carpeta,
            @Value("${sga.imagenes.cloudinary.tiempo-espera:PT20S}") Duration tiempoEspera) {
        String elegido = proveedor == null ? "" : proveedor.trim().toLowerCase(Locale.ROOT);
        AlmacenImagenes almacen = switch (elegido) {
            case "cloudinary" -> new AlmacenImagenesCloudinary(urlCloudinary, cloudName, apiKey, apiSecret, carpeta,
                    tiempoEspera, Clock.systemUTC());
            case "local" -> new AlmacenImagenesLocal(Path.of(directorio), urlPublica);
            default -> {
                log.warn("Imágenes: proveedor desconocido '{}' en sga.imagenes.proveedor; se usa el local", proveedor);
                yield new AlmacenImagenesLocal(Path.of(directorio), urlPublica);
            }
        };
        log.info("Imágenes: fotos de los apartamentos con {}", almacen.getClass().getSimpleName());
        return almacen;
    }
}

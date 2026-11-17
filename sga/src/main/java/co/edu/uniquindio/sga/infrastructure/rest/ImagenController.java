package co.edu.uniquindio.sga.infrastructure.rest;

import co.edu.uniquindio.sga.application.dto.response.ImagenSubidaResponse;
import co.edu.uniquindio.sga.application.exception.ArchivoImagenInvalidoException;
import co.edu.uniquindio.sga.application.usecase.SubirImagenApartamentoUseCase;
import co.edu.uniquindio.sga.domain.port.AlmacenImagenes;
import co.edu.uniquindio.sga.infrastructure.imagenes.AlmacenImagenesLocal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;

/**
 * Adaptador REST de las fotos de los apartamentos. POST sube una foto al almacén configurado
 * (sga.imagenes.proveedor) y devuelve su URL; GET sirve las fotos que guardó el almacén local. Los errores los
 * traduce GlobalExceptionHandler: 400 si el archivo no sirve, 502 si el almacén falla.
 */
@RestController
@RequestMapping("/api/imagenes")
@RequiredArgsConstructor
@Tag(name = "Apartamentos", description = "Administración, búsqueda y preparación de apartamentos")
public class ImagenController {

    private final SubirImagenApartamentoUseCase subirImagenApartamentoUseCase;
    private final AlmacenImagenes almacenImagenes;

    /** POST /api/imagenes (multipart/form-data, parte "archivo") */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('ADMINISTRADOR')")
    @Operation(summary = "Subir una foto de apartamento",
            description = "Guarda la foto (JPG, PNG o WebP de hasta 5 MB) en el almacén configurado en "
                    + "sga.imagenes.proveedor y devuelve su URL, que luego va en las imágenes del apartamento")
    @ApiResponse(responseCode = "201", description = "Foto guardada, con su URL")
    @ApiResponse(responseCode = "400", description = "Falta el archivo, está vacío o no es JPG, PNG ni WebP")
    @ApiResponse(responseCode = "401", description = "Sin token o token inválido")
    @ApiResponse(responseCode = "403", description = "El rol no lo permite")
    @ApiResponse(responseCode = "413", description = "La foto supera los 5 MB")
    @ApiResponse(responseCode = "502", description = "El almacén de imágenes falló o tardó demasiado")
    public ResponseEntity<ImagenSubidaResponse> subir(@RequestPart("archivo") MultipartFile archivo) {
        byte[] contenido;
        try {
            contenido = archivo.getBytes();
        } catch (IOException e) {
            throw new ArchivoImagenInvalidoException("No se pudo leer la foto");
        }
        String url = subirImagenApartamentoUseCase.ejecutar(contenido);
        return ResponseEntity.status(HttpStatus.CREATED).body(new ImagenSubidaResponse(url));
    }

    /** GET /api/imagenes/{nombre}: solo existe con el almacén local */
    @GetMapping("/{nombre}")
    @PreAuthorize("permitAll()")
    @Operation(summary = "Foto guardada en el almacén local",
            description = "Pública. Con el almacén de Cloudinary las fotos se sirven desde Cloudinary y aquí da 404")
    @ApiResponse(responseCode = "200", description = "La foto")
    @ApiResponse(responseCode = "404", description = "No existe o el almacén no es el local")
    public ResponseEntity<byte[]> ver(@PathVariable String nombre) {
        if (!(almacenImagenes instanceof AlmacenImagenesLocal local)) {
            return ResponseEntity.notFound().build();
        }
        return local.leer(nombre)
                .map(imagen -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(imagen.formato().tipoContenido()))
                        .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic())
                        .header("X-Content-Type-Options", "nosniff")
                        .body(imagen.contenido()))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}

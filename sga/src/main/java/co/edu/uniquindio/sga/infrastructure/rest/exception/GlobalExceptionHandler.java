package co.edu.uniquindio.sga.infrastructure.rest.exception;

import co.edu.uniquindio.sga.application.exception.ApartamentoNoEncontradoException;
import co.edu.uniquindio.sga.application.exception.ArchivoImagenInvalidoException;
import co.edu.uniquindio.sga.application.exception.CanalVentaNoEncontradoException;
import co.edu.uniquindio.sga.application.exception.ConflictoCanalNoEncontradoException;
import co.edu.uniquindio.sga.application.exception.CodigoRecuperacionInvalidoException;
import co.edu.uniquindio.sga.application.exception.EdadMinimaCuentaException;
import co.edu.uniquindio.sga.application.exception.ContrasenaActualIncorrectaException;
import co.edu.uniquindio.sga.application.exception.ContrasenaNuevaIgualALaActualException;
import co.edu.uniquindio.sga.application.exception.MedioPagoNoEncontradoException;
import co.edu.uniquindio.sga.application.exception.RolPropioNoModificableException;
import co.edu.uniquindio.sga.application.exception.BloqueoNoEncontradoException;
import co.edu.uniquindio.sga.application.exception.FolioNoEncontradoException;
import co.edu.uniquindio.sga.application.exception.NovedadNoEncontradaException;
import co.edu.uniquindio.sga.application.exception.ReservaNoEncontradaException;
import co.edu.uniquindio.sga.application.exception.ServicioAdicionalNoEncontradoException;
import co.edu.uniquindio.sga.application.exception.TemporadaNoEncontradaException;
import co.edu.uniquindio.sga.application.exception.UsuarioNoDesactivableException;
import co.edu.uniquindio.sga.application.exception.UsuarioNoEncontradoException;
import co.edu.uniquindio.sga.application.exception.UsuarioYaRegistradoException;
import co.edu.uniquindio.sga.domain.exception.GrupoSinOcupanteFacturableException;
import co.edu.uniquindio.sga.domain.exception.ImagenNoAlmacenadaException;
import co.edu.uniquindio.sga.domain.exception.ReglaDominioException;
import co.edu.uniquindio.sga.domain.exception.TitularMenorDeEdadException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Adaptador de salida de errores: traduce las excepciones del SGA a códigos HTTP (Guía 07, 4.6).
 * El dominio no conoce HTTP; esta clase es la única que decide qué código recibe el cliente.
 * Convención ampliada (3.14): 401 no sé quién eres · 403 no tienes permiso.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 400: falló una anotación del DTO (@NotBlank, @NotNull, @Email, @Positive...). */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> validacion(MethodArgumentNotValidException ex, HttpServletRequest req) {
        Map<String, String> errores = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(e -> errores.putIfAbsent(e.getField(), e.getDefaultMessage()));
        return construir(HttpStatus.BAD_REQUEST, "La petición tiene datos inválidos", req, errores);
    }

    /** 400: falló una anotación de un parámetro de la URL, por ejemplo @Min en pagina. */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponse> validacionParametros(HandlerMethodValidationException ex,
                                                              HttpServletRequest req) {
        Map<String, String> errores = new LinkedHashMap<>();
        ex.getParameterValidationResults().forEach(resultado -> resultado.getResolvableErrors()
                .forEach(e -> errores.putIfAbsent(resultado.getMethodParameter().getParameterName(),
                        e.getDefaultMessage())));
        return construir(HttpStatus.BAD_REQUEST, "La petición tiene datos inválidos", req, errores);
    }

    /** 400: falta un parámetro obligatorio de la URL. */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> parametroFaltante(MissingServletRequestParameterException ex,
                                                           HttpServletRequest req) {
        return construir(HttpStatus.BAD_REQUEST, "La petición tiene datos inválidos", req,
                Map.of(ex.getParameterName(), "El parámetro es obligatorio"));
    }

    /** 400: falta la parte "archivo" de la subida de una foto. */
    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ErrorResponse> parteFaltante(MissingServletRequestPartException ex, HttpServletRequest req) {
        return construir(HttpStatus.BAD_REQUEST, "La petición tiene datos inválidos", req,
                Map.of(ex.getRequestPartName(), "Selecciona una foto"));
    }

    /** 400: el archivo subido no sirve como foto (vacío, muy grande o de otro formato). */
    @ExceptionHandler(ArchivoImagenInvalidoException.class)
    public ResponseEntity<ErrorResponse> archivoImagenInvalido(ArchivoImagenInvalidoException ex,
                                                               HttpServletRequest req) {
        return construir(HttpStatus.BAD_REQUEST, "La petición tiene datos inválidos", req,
                Map.of("archivo", ex.getMessage()));
    }

    /** 413: la foto supera el tamaño máximo que admite el servidor. */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> archivoMuyGrande(MaxUploadSizeExceededException ex, HttpServletRequest req) {
        return construir(HttpStatus.CONTENT_TOO_LARGE, "La foto supera los 5 MB", req,
                Map.of("archivo", "La foto supera los 5 MB"));
    }

    /** 502: el almacén de imágenes (Cloudinary o el disco) no pudo guardar la foto. */
    @ExceptionHandler(ImagenNoAlmacenadaException.class)
    public ResponseEntity<ErrorResponse> imagenNoAlmacenada(ImagenNoAlmacenadaException ex, HttpServletRequest req) {
        return construir(HttpStatus.BAD_GATEWAY, ex.getMessage(), req, null);
    }

    /** 400: JSON mal formado, fecha imposible o valor de enum que no existe. */
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ErrorResponse> formato(Exception ex, HttpServletRequest req) {
        return construir(HttpStatus.BAD_REQUEST,
                "Formato inválido: revise el JSON, las fechas (AAAA-MM-DD) y los valores permitidos", req, null);
    }

    /** 400: la contraseña actual del cambio de contraseña no coincide (Guía 12, 7.2). */
    @ExceptionHandler(ContrasenaActualIncorrectaException.class)
    public ResponseEntity<ErrorResponse> contrasenaActualIncorrecta(ContrasenaActualIncorrectaException ex,
                                                                    HttpServletRequest req) {
        return construir(HttpStatus.BAD_REQUEST, ex.getMessage(), req, null);
    }

    /** 400: la contraseña nueva es la misma que la actual; se reporta sobre el campo contrasenaNueva. */
    @ExceptionHandler(ContrasenaNuevaIgualALaActualException.class)
    public ResponseEntity<ErrorResponse> contrasenaNuevaIgual(ContrasenaNuevaIgualALaActualException ex,
                                                              HttpServletRequest req) {
        return construir(HttpStatus.BAD_REQUEST, "La petición tiene datos inválidos", req,
                Map.of("contrasenaNueva", ex.getMessage()));
    }

    /**
     * 400: el grupo no tiene ningún ocupante facturable. Es una regla del dominio, pero lo que llega mal es la
     * composición del grupo, no un conflicto con el estado del sistema. Gana sobre el 409 de ReglaDominioException
     * porque es una subclase más específica.
     */
    @ExceptionHandler(GrupoSinOcupanteFacturableException.class)
    public ResponseEntity<ErrorResponse> grupoSinFacturable(GrupoSinOcupanteFacturableException ex,
                                                            HttpServletRequest req) {
        return construir(HttpStatus.BAD_REQUEST, ex.getMessage(), req, null);
    }

    /**
     * 400: en una reserva de la recepción el titular no alcanza la edad mínima. Se reporta sobre el campo
     * titular.fechaNacimiento para que el cliente lo muestre junto a esa fecha.
     */
    @ExceptionHandler(TitularMenorDeEdadException.class)
    public ResponseEntity<ErrorResponse> titularMenorDeEdad(TitularMenorDeEdadException ex, HttpServletRequest req) {
        return construir(HttpStatus.BAD_REQUEST, "La petición tiene datos inválidos", req,
                Map.of("titular.fechaNacimiento", ex.getMessage()));
    }

    /** 400: quien crea la cuenta no tiene la edad mínima; se reporta sobre el campo fechaNacimiento. */
    @ExceptionHandler(EdadMinimaCuentaException.class)
    public ResponseEntity<ErrorResponse> edadMinimaCuenta(EdadMinimaCuentaException ex, HttpServletRequest req) {
        return construir(HttpStatus.BAD_REQUEST, "La petición tiene datos inválidos", req,
                Map.of("fechaNacimiento", ex.getMessage()));
    }

    /** 400: el código para restablecer la contraseña no sirve (7.2). Mismo mensaje en todos los casos. */
    @ExceptionHandler(CodigoRecuperacionInvalidoException.class)
    public ResponseEntity<ErrorResponse> codigoRecuperacionInvalido(CodigoRecuperacionInvalidoException ex,
                                                                    HttpServletRequest req) {
        return construir(HttpStatus.BAD_REQUEST, ex.getMessage(), req, null);
    }

    /** 404: el identificador no existe (excepciones de aplicación). */
    @ExceptionHandler({ReservaNoEncontradaException.class, ApartamentoNoEncontradoException.class,
            FolioNoEncontradoException.class, TemporadaNoEncontradaException.class, BloqueoNoEncontradoException.class,
            NovedadNoEncontradaException.class, CanalVentaNoEncontradoException.class,
            ConflictoCanalNoEncontradoException.class, ServicioAdicionalNoEncontradoException.class,
            MedioPagoNoEncontradoException.class})
    public ResponseEntity<ErrorResponse> noEncontrado(RuntimeException ex, HttpServletRequest req) {
        return construir(HttpStatus.NOT_FOUND, ex.getMessage(), req, null);
    }

    /** 409: toda regla violada, RN-01 a RN-22 y RP-01 a RP-03. La única excepción de dominio del SGA. */
    @ExceptionHandler(ReglaDominioException.class)
    public ResponseEntity<ErrorResponse> reglaDominio(ReglaDominioException ex, HttpServletRequest req) {
        return construir(HttpStatus.CONFLICT, ex.getMessage(), req, null);
    }

    /** 409: el correo o el documento del registro ya pertenecen a otro usuario (Guía 12). */
    @ExceptionHandler(UsuarioYaRegistradoException.class)
    public ResponseEntity<ErrorResponse> usuarioYaRegistrado(UsuarioYaRegistradoException ex, HttpServletRequest req) {
        return construir(HttpStatus.CONFLICT, ex.getMessage(), req, null);
    }

    /** 404: el usuario pedido no existe (Guía 12, 7.2). */
    @ExceptionHandler(UsuarioNoEncontradoException.class)
    public ResponseEntity<ErrorResponse> usuarioNoEncontrado(UsuarioNoEncontradoException ex, HttpServletRequest req) {
        return construir(HttpStatus.NOT_FOUND, ex.getMessage(), req, null);
    }

    /**
     * 409: el usuario es titular de reservas vigentes, o el administrador intenta desactivarse o cambiar su propio
     * rol (Guía 12, 7.2).
     */
    @ExceptionHandler({UsuarioNoDesactivableException.class, RolPropioNoModificableException.class})
    public ResponseEntity<ErrorResponse> cuentaNoModificable(RuntimeException ex, HttpServletRequest req) {
        return construir(HttpStatus.CONFLICT, ex.getMessage(), req, null);
    }

    /** 401: credenciales inválidas o usuario inactivo en el login (Guía 12). */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> autenticacion(AuthenticationException ex, HttpServletRequest req) {
        // Mensaje genérico: no revela si el correo existe o si la contraseña era la errada
        return construir(HttpStatus.UNAUTHORIZED, "Credenciales inválidas", req, null);
    }

    /** 403: autenticado, pero sin permiso por rol o por propietario (Guía 12). */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> accesoDenegado(AccessDeniedException ex, HttpServletRequest req) {
        return construir(HttpStatus.FORBIDDEN, "No tiene permiso para realizar esta operación", req, null);
    }

    /** 500: lo inesperado. La traza va al log, nunca al cliente. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> generico(Exception ex, HttpServletRequest req) {
        // Los errores propios de Spring (ruta inexistente 404, método no permitido 405) traen su propio código
        if (ex instanceof org.springframework.web.ErrorResponse deSpring) {
            HttpStatus status = HttpStatus.valueOf(deSpring.getStatusCode().value());
            return construir(status, status.getReasonPhrase(), req, null);
        }
        log.error("Error inesperado en {}", req.getRequestURI(), ex);
        return construir(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno del servidor", req, null);
    }

    private ResponseEntity<ErrorResponse> construir(HttpStatus status, String mensaje,
                                                    HttpServletRequest req, Map<String, String> errores) {
        return ResponseEntity.status(status).body(new ErrorResponse(Instant.now(), status.value(),
                status.getReasonPhrase(), mensaje, req.getRequestURI(), errores));
    }
}
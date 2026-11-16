package co.edu.uniquindio.sga.infrastructure.rest;

import co.edu.uniquindio.sga.application.dto.request.ActualizarAlojamientoRequest;
import co.edu.uniquindio.sga.application.dto.request.ActualizarServicioAdicionalRequest;
import co.edu.uniquindio.sga.application.dto.request.RegistrarServicioAdicionalRequest;
import co.edu.uniquindio.sga.application.dto.response.AlojamientoResponse;
import co.edu.uniquindio.sga.application.usecase.ConfiguracionAlojamientoUseCase;
import co.edu.uniquindio.sga.application.usecase.ServiciosAdicionalesUseCase;
import co.edu.uniquindio.sga.application.usecase.ServiciosAdicionalesUseCase.DatosServicio;
import co.edu.uniquindio.sga.domain.entity.Alojamiento;
import co.edu.uniquindio.sga.domain.entity.ServicioAdicional;
import co.edu.uniquindio.sga.domain.valueobject.DepositoGarantia;
import co.edu.uniquindio.sga.infrastructure.rest.mapper.ConfiguracionMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

/**
 * Adaptador REST del alojamiento (Guía 08): CU-28 y la gestión de sus servicios adicionales. El sistema administra
 * un único alojamiento (F-13), por eso la ruta no lleva id. No tiene reglas ni try/catch: los errores los traduce
 * GlobalExceptionHandler (400, 404, 409).
 */
@RestController
@RequestMapping("/api/alojamiento")
@RequiredArgsConstructor
@Tag(name = "Configuración del alojamiento", description = "Alojamiento, temporadas y política de cancelación")
public class AlojamientoController {

    private final ConfiguracionAlojamientoUseCase configuracionAlojamientoUseCase;
    private final ServiciosAdicionalesUseCase serviciosAdicionalesUseCase;
    private final ConfiguracionMapper mapper;
    /** RP-02: el mismo depósito que se asienta en el folio (bean de DomainServiceConfig) */
    private final DepositoGarantia depositoGarantia;

    /** CU-28. GET /api/alojamiento */
    @GetMapping
    @PreAuthorize("permitAll()")
    @Operation(summary = "Consultar el alojamiento",
            description = "Datos, ubicación, horario, normas de convivencia, servicios adicionales (activos e "
                    + "inactivos, con el motivo si alguno no se puede desactivar) y parámetros vigentes (solo "
                    + "lectura: se configuran en application.properties)")
    @ApiResponse(responseCode = "200", description = "El alojamiento")
    public ResponseEntity<AlojamientoResponse> consultar() {
        return ResponseEntity.ok(respuesta(configuracionAlojamientoUseCase.consultar()));
    }

    /** CU-28. PUT /api/alojamiento */
    @PutMapping
    @PreAuthorize("hasAnyAuthority('ADMINISTRADOR')")
    @Operation(summary = "Actualizar los datos del alojamiento",
            description = "Reemplaza nombre, descripción, ciudad, dirección, ubicación y normas de convivencia. El "
                    + "horario (L-12) es opcional: si no llega ninguna hora se conserva el vigente. Los parámetros "
                    + "no se cambian por API")
    @ApiResponse(responseCode = "200", description = "Alojamiento actualizado")
    @ApiResponse(responseCode = "400", description = "Falta un dato obligatorio o una hora está mal escrita")
    @ApiResponse(responseCode = "401", description = "Sin token o token inválido")
    @ApiResponse(responseCode = "403", description = "El rol no lo permite")
    @ApiResponse(responseCode = "409", description = "Coordenada fuera de rango (7.3) o falta una de las dos horas "
            + "del horario (L-12)")
    public ResponseEntity<AlojamientoResponse> actualizar(@Valid @RequestBody ActualizarAlojamientoRequest request) {
        Alojamiento alojamiento = configuracionAlojamientoUseCase.actualizar(mapper.aDatos(request));
        return ResponseEntity.ok(respuesta(alojamiento));
    }

    // ==================== Servicios adicionales (L-17) ====================

    /** POST /api/alojamiento/servicios */
    @PostMapping("/servicios")
    @PreAuthorize("hasAnyAuthority('ADMINISTRADOR')")
    @Operation(summary = "Crear un servicio adicional",
            description = "Registra un servicio activo con su id (letras, números y guiones; se guarda en mayúsculas), "
                    + "nombre, descripción, si genera cargo y su valor. Un servicio con cargo exige valor mayor que cero")
    @ApiResponse(responseCode = "201", description = "Servicio creado; Location apunta a su consulta")
    @ApiResponse(responseCode = "400", description = "Falta un dato obligatorio o el valor es negativo")
    @ApiResponse(responseCode = "401", description = "Sin token o token inválido")
    @ApiResponse(responseCode = "403", description = "El rol no lo permite")
    @ApiResponse(responseCode = "409", description = "El id ya existe o tiene un formato inválido, o un servicio con "
            + "cargo llega sin valor")
    public ResponseEntity<AlojamientoResponse.ServicioAdicionalResponse> registrarServicio(
            @Valid @RequestBody RegistrarServicioAdicionalRequest request) {
        ServicioAdicional servicio = serviciosAdicionalesUseCase.registrar(request.id(),
                new DatosServicio(request.nombre(), request.descripcion(), request.generaCargo(), request.valor()));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
                .buildAndExpand(servicio.getId()).toUri();
        return ResponseEntity.created(location).body(servicioResponse(servicio));
    }

    /** GET /api/alojamiento/servicios/{id} */
    @GetMapping("/servicios/{id}")
    @PreAuthorize("hasAnyAuthority('ADMINISTRADOR')")
    @Operation(summary = "Consultar un servicio adicional", description = "Es el recurso del Location de la creación")
    @ApiResponse(responseCode = "200", description = "El servicio")
    @ApiResponse(responseCode = "401", description = "Sin token o token inválido")
    @ApiResponse(responseCode = "403", description = "El rol no lo permite")
    @ApiResponse(responseCode = "404", description = "No existe el servicio")
    public ResponseEntity<AlojamientoResponse.ServicioAdicionalResponse> consultarServicio(@PathVariable String id) {
        return ResponseEntity.ok(servicioResponse(serviciosAdicionalesUseCase.consultar(id)));
    }

    /** PUT /api/alojamiento/servicios/{id} */
    @PutMapping("/servicios/{id}")
    @PreAuthorize("hasAnyAuthority('ADMINISTRADOR')")
    @Operation(summary = "Editar un servicio adicional",
            description = "Reemplaza nombre, descripción, si genera cargo y valor. Los cargos ya asentados en los "
                    + "folios no cambian (RN-16). El servicio de recepción nocturna debe seguir generando cargo (RP-03)")
    @ApiResponse(responseCode = "200", description = "Servicio actualizado")
    @ApiResponse(responseCode = "400", description = "Falta un dato obligatorio o el valor es negativo")
    @ApiResponse(responseCode = "401", description = "Sin token o token inválido")
    @ApiResponse(responseCode = "403", description = "El rol no lo permite")
    @ApiResponse(responseCode = "404", description = "No existe el servicio")
    @ApiResponse(responseCode = "409", description = "Un servicio con cargo llega sin valor, o se quiere dejar sin "
            + "cargo el servicio de recepción nocturna (RP-03)")
    public ResponseEntity<AlojamientoResponse.ServicioAdicionalResponse> actualizarServicio(
            @PathVariable String id, @Valid @RequestBody ActualizarServicioAdicionalRequest request) {
        ServicioAdicional servicio = serviciosAdicionalesUseCase.actualizar(id,
                new DatosServicio(request.nombre(), request.descripcion(), request.generaCargo(), request.valor()));
        return ResponseEntity.ok(servicioResponse(servicio));
    }

    /** PUT /api/alojamiento/servicios/{id}/desactivar. No hay DELETE: la eliminación es lógica (12.4). */
    @PutMapping("/servicios/{id}/desactivar")
    @PreAuthorize("hasAnyAuthority('ADMINISTRADOR')")
    @Operation(summary = "Desactivar un servicio adicional",
            description = "Eliminación lógica: deja de ofrecerse y de cargarse a los folios; los cargos que ya generó "
                    + "se conservan. El servicio de recepción nocturna no se desactiva (RP-03)")
    @ApiResponse(responseCode = "200", description = "Servicio desactivado")
    @ApiResponse(responseCode = "401", description = "Sin token o token inválido")
    @ApiResponse(responseCode = "403", description = "El rol no lo permite")
    @ApiResponse(responseCode = "404", description = "No existe el servicio")
    @ApiResponse(responseCode = "409", description = "Ya estaba desactivado o es el servicio de recepción nocturna "
            + "(RP-03)")
    public ResponseEntity<AlojamientoResponse.ServicioAdicionalResponse> desactivarServicio(@PathVariable String id) {
        return ResponseEntity.ok(servicioResponse(serviciosAdicionalesUseCase.desactivar(id)));
    }

    /** PUT /api/alojamiento/servicios/{id}/activar */
    @PutMapping("/servicios/{id}/activar")
    @PreAuthorize("hasAnyAuthority('ADMINISTRADOR')")
    @Operation(summary = "Volver a activar un servicio adicional")
    @ApiResponse(responseCode = "200", description = "Servicio activo")
    @ApiResponse(responseCode = "401", description = "Sin token o token inválido")
    @ApiResponse(responseCode = "403", description = "El rol no lo permite")
    @ApiResponse(responseCode = "404", description = "No existe el servicio")
    @ApiResponse(responseCode = "409", description = "Ya estaba activo")
    public ResponseEntity<AlojamientoResponse.ServicioAdicionalResponse> activarServicio(@PathVariable String id) {
        return ResponseEntity.ok(servicioResponse(serviciosAdicionalesUseCase.activar(id)));
    }

    private AlojamientoResponse respuesta(Alojamiento alojamiento) {
        return mapper.aAlojamientoResponse(alojamiento, depositoGarantia,
                id -> serviciosAdicionalesUseCase.motivoObligatorio(id).orElse(null));
    }

    private AlojamientoResponse.ServicioAdicionalResponse servicioResponse(ServicioAdicional servicio) {
        return mapper.aServicioResponse(servicio,
                serviciosAdicionalesUseCase.motivoObligatorio(servicio.getId()).orElse(null));
    }
}

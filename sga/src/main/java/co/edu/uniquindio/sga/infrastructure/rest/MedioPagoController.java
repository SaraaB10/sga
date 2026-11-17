package co.edu.uniquindio.sga.infrastructure.rest;

import co.edu.uniquindio.sga.application.dto.request.CrearMedioPagoRequest;
import co.edu.uniquindio.sga.application.dto.response.MedioPagoResponse;
import co.edu.uniquindio.sga.application.usecase.MediosPagoUseCase;
import co.edu.uniquindio.sga.domain.entity.MedioPagoAceptado;
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
import java.util.List;

/**
 * Adaptador REST del catálogo de medios de pago aceptados (L-18). Los activos son públicos: el huésped los ve junto
 * a la política del apartamento y la recepción los usa al registrar un pago. Los errores los traduce
 * GlobalExceptionHandler (400, 404, 409).
 */
@RestController
@RequestMapping("/api/medios-pago")
@RequiredArgsConstructor
@Tag(name = "Configuración del alojamiento", description = "Alojamiento, temporadas y política de cancelación")
public class MedioPagoController {

    private final MediosPagoUseCase mediosPagoUseCase;

    /** GET /api/medios-pago */
    @GetMapping
    @PreAuthorize("permitAll()")
    @Operation(summary = "Medios de pago aceptados", description = "Solo los activos, en el orden del catálogo")
    @ApiResponse(responseCode = "200", description = "Medios de pago activos")
    public ResponseEntity<List<MedioPagoResponse>> activos() {
        return ResponseEntity.ok(mediosPagoUseCase.listarActivos().stream().map(this::aResponse).toList());
    }

    /** GET /api/medios-pago/todos */
    @GetMapping("/todos")
    @PreAuthorize("hasAuthority('ADMINISTRADOR')")
    @Operation(summary = "Catálogo completo de medios de pago", description = "Activos e inactivos")
    @ApiResponse(responseCode = "200", description = "Todos los medios de pago")
    @ApiResponse(responseCode = "401", description = "Sin token o token inválido")
    @ApiResponse(responseCode = "403", description = "El rol no lo permite")
    public ResponseEntity<List<MedioPagoResponse>> todos() {
        return ResponseEntity.ok(mediosPagoUseCase.listarTodos().stream().map(this::aResponse).toList());
    }

    /** POST /api/medios-pago */
    @PostMapping
    @PreAuthorize("hasAuthority('ADMINISTRADOR')")
    @Operation(summary = "Agregar un medio de pago", description = "Nace activo; el código se toma del nombre")
    @ApiResponse(responseCode = "201", description = "Medio creado, con Location")
    @ApiResponse(responseCode = "400", description = "Falta el nombre o no sirve para formar el código")
    @ApiResponse(responseCode = "401", description = "Sin token o token inválido")
    @ApiResponse(responseCode = "403", description = "El rol no lo permite")
    @ApiResponse(responseCode = "409", description = "Ya existe un medio con ese código")
    public ResponseEntity<MedioPagoResponse> crear(@Valid @RequestBody CrearMedioPagoRequest request) {
        MedioPagoAceptado medio = mediosPagoUseCase.crear(request.nombre());
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/medios-pago/todos").build().toUri();
        return ResponseEntity.created(location).body(aResponse(medio));
    }

    /** PUT /api/medios-pago/{codigo}/activar */
    @PutMapping("/{codigo}/activar")
    @PreAuthorize("hasAuthority('ADMINISTRADOR')")
    @Operation(summary = "Activar un medio de pago")
    @ApiResponse(responseCode = "200", description = "Medio activado")
    @ApiResponse(responseCode = "401", description = "Sin token o token inválido")
    @ApiResponse(responseCode = "403", description = "El rol no lo permite")
    @ApiResponse(responseCode = "404", description = "No existe el medio")
    @ApiResponse(responseCode = "409", description = "Ya estaba activo")
    public ResponseEntity<MedioPagoResponse> activar(@PathVariable String codigo) {
        return ResponseEntity.ok(aResponse(mediosPagoUseCase.activar(codigo)));
    }

    /** PUT /api/medios-pago/{codigo}/desactivar */
    @PutMapping("/{codigo}/desactivar")
    @PreAuthorize("hasAuthority('ADMINISTRADOR')")
    @Operation(summary = "Desactivar un medio de pago",
            description = "Eliminación lógica: deja de ofrecerse, pero los pagos registrados conservan su medio")
    @ApiResponse(responseCode = "200", description = "Medio desactivado")
    @ApiResponse(responseCode = "401", description = "Sin token o token inválido")
    @ApiResponse(responseCode = "403", description = "El rol no lo permite")
    @ApiResponse(responseCode = "404", description = "No existe el medio")
    @ApiResponse(responseCode = "409", description = "Ya estaba desactivado o es el último activo")
    public ResponseEntity<MedioPagoResponse> desactivar(@PathVariable String codigo) {
        return ResponseEntity.ok(aResponse(mediosPagoUseCase.desactivar(codigo)));
    }

    private MedioPagoResponse aResponse(MedioPagoAceptado medio) {
        return new MedioPagoResponse(medio.getMedio().codigo(), medio.getNombre(), medio.isActivo());
    }
}

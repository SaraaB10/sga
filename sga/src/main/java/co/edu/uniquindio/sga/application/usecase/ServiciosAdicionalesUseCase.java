package co.edu.uniquindio.sga.application.usecase;

import co.edu.uniquindio.sga.application.exception.ServicioAdicionalNoEncontradoException;
import co.edu.uniquindio.sga.domain.entity.Alojamiento;
import co.edu.uniquindio.sga.domain.entity.ServicioAdicional;
import co.edu.uniquindio.sga.domain.exception.ReglaDominioException;
import co.edu.uniquindio.sga.domain.repository.AlojamientoRepository;
import co.edu.uniquindio.sga.domain.service.GestionServiciosAdicionalesService;
import co.edu.uniquindio.sga.domain.valueobject.Dinero;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.RoundingMode;
import java.util.Objects;
import java.util.Optional;

/**
 * POST /api/alojamiento/servicios, GET y PUT /api/alojamiento/servicios/{id}, PUT .../desactivar y .../activar
 * Gestión de los servicios adicionales del alojamiento: crear, editar (nombre, descripción, si genera cargo y valor),
 * desactivar (eliminación lógica) y volver a activar. Las reglas las aplica GestionServiciosAdicionalesService (409);
 * si el servicio no existe responde 404.
 * Actor: administrador.
 */
@Service
public class ServiciosAdicionalesUseCase {

    /** Datos de un servicio. valor se ignora si no genera cargo. */
    public record DatosServicio(String nombre, String descripcion, boolean generaCargo, Long valor) {
    }

    private final AlojamientoRepository alojamientoRepository;
    private final GestionServiciosAdicionalesService gestionService;

    public ServiciosAdicionalesUseCase(AlojamientoRepository alojamientoRepository,
                                       GestionServiciosAdicionalesService gestionService) {
        this.alojamientoRepository = Objects.requireNonNull(alojamientoRepository);
        this.gestionService = Objects.requireNonNull(gestionService);
    }

    @Transactional
    public ServicioAdicional registrar(String id, DatosServicio datos) {
        requerido(datos);
        return gestionService.registrar(id, datos.nombre(), datos.descripcion(), datos.generaCargo(), valor(datos));
    }

    @Transactional(readOnly = true)
    public ServicioAdicional consultar(String id) {
        validarQueExiste(id);
        return gestionService.consultar(id);
    }

    @Transactional
    public ServicioAdicional actualizar(String id, DatosServicio datos) {
        requerido(datos);
        validarQueExiste(id);
        return gestionService.actualizar(id, datos.nombre(), datos.descripcion(), datos.generaCargo(), valor(datos));
    }

    @Transactional
    public ServicioAdicional desactivar(String id) {
        validarQueExiste(id);
        return gestionService.desactivar(id);
    }

    @Transactional
    public ServicioAdicional activar(String id) {
        validarQueExiste(id);
        return gestionService.activar(id);
    }

    /** Por qué el servicio no puede desactivarse ni dejar de generar cargo; vacío si se puede. */
    public Optional<String> motivoObligatorio(String id) {
        return gestionService.motivoObligatorio(id);
    }

    /** RP-03: valor del recargo de recepción nocturna en pesos; vacío si el servicio no existe. */
    public Optional<Long> recargoRecepcionNocturna(Alojamiento alojamiento) {
        return gestionService.recargoRecepcionNocturna(alojamiento)
                .filter(Objects::nonNull)
                .map(valor -> valor.monto().setScale(0, RoundingMode.HALF_UP).longValueExact());
    }

    private void validarQueExiste(String id) {
        boolean existe = alojamientoRepository.obtener()
                .flatMap(alojamiento -> alojamiento.buscarServicioAdicional(id))
                .isPresent();
        if (!existe) {
            throw new ServicioAdicionalNoEncontradoException(id);
        }
    }

    private static Dinero valor(DatosServicio datos) {
        return datos.valor() == null ? null : Dinero.de(datos.valor());
    }

    private static void requerido(DatosServicio datos) {
        if (datos == null) throw new ReglaDominioException("Los datos del servicio son obligatorios");
    }
}

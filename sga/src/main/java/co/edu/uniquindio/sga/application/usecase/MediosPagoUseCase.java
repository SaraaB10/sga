package co.edu.uniquindio.sga.application.usecase;

import co.edu.uniquindio.sga.application.exception.MedioPagoNoEncontradoException;
import co.edu.uniquindio.sga.domain.entity.MedioPagoAceptado;
import co.edu.uniquindio.sga.domain.exception.ReglaDominioException;
import co.edu.uniquindio.sga.domain.repository.MedioPagoRepository;
import co.edu.uniquindio.sga.domain.valueobject.MedioPago;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/**
 * GET /api/medios-pago (activos, público), GET /api/medios-pago/todos, POST /api/medios-pago y
 * PUT /api/medios-pago/{codigo}/activar y /desactivar (administrador).
 * Catálogo de medios de pago aceptados (L-18). Solo los activos se ofrecen al registrar un pago y se muestran al
 * huésped; la baja es lógica para que los pagos ya registrados conserven su medio (RN-16).
 */
@Service
public class MediosPagoUseCase {

    private final MedioPagoRepository medioPagoRepository;

    public MediosPagoUseCase(MedioPagoRepository medioPagoRepository) {
        this.medioPagoRepository = Objects.requireNonNull(medioPagoRepository);
    }

    @Transactional(readOnly = true)
    public List<MedioPagoAceptado> listarActivos() {
        return medioPagoRepository.listarActivos();
    }

    @Transactional(readOnly = true)
    public List<MedioPagoAceptado> listarTodos() {
        return medioPagoRepository.listarTodos();
    }

    /** Medio nuevo y activo; el código se toma del nombre. 409 si ya existe un medio con ese código */
    @Transactional
    public MedioPagoAceptado crear(String nombre) {
        MedioPagoAceptado nuevo = MedioPagoAceptado.crear(null, nombre);
        if (medioPagoRepository.buscar(nuevo.getMedio()).isPresent()) {
            throw new ReglaDominioException("L-18: ya existe el medio de pago " + nuevo.getMedio());
        }
        return medioPagoRepository.guardar(nuevo);
    }

    @Transactional
    public MedioPagoAceptado activar(String codigo) {
        MedioPagoAceptado medio = buscar(codigo);
        medio.activar();
        return medioPagoRepository.guardar(medio);
    }

    /** 409 si es el último activo: sin ningún medio no se podría registrar ningún pago */
    @Transactional
    public MedioPagoAceptado desactivar(String codigo) {
        MedioPagoAceptado medio = buscar(codigo);
        if (medio.isActivo() && medioPagoRepository.listarActivos().size() == 1) {
            throw new ReglaDominioException("L-18: debe quedar al menos un medio de pago activo");
        }
        medio.desactivar();
        return medioPagoRepository.guardar(medio);
    }

    private MedioPagoAceptado buscar(String codigo) {
        MedioPago medio;
        try {
            medio = new MedioPago(codigo);
        } catch (ReglaDominioException e) {
            throw new MedioPagoNoEncontradoException(codigo);
        }
        return medioPagoRepository.buscar(medio).orElseThrow(() -> new MedioPagoNoEncontradoException(codigo));
    }
}

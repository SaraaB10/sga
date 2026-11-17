package co.edu.uniquindio.sga.domain.repository;

import co.edu.uniquindio.sga.domain.entity.MedioPagoAceptado;
import co.edu.uniquindio.sga.domain.valueobject.MedioPago;

import java.util.List;
import java.util.Optional;

/** Puerto de persistencia del catálogo de medios de pago aceptados (L-18). */
public interface MedioPagoRepository {

    MedioPagoAceptado guardar(MedioPagoAceptado medio);

    Optional<MedioPagoAceptado> buscar(MedioPago medio);

    /** Todos, activos e inactivos, en el orden en que se agregaron */
    List<MedioPagoAceptado> listarTodos();

    default List<MedioPagoAceptado> listarActivos() {
        return listarTodos().stream().filter(MedioPagoAceptado::isActivo).toList();
    }
}

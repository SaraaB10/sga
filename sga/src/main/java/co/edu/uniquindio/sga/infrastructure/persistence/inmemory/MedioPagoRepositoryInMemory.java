package co.edu.uniquindio.sga.infrastructure.persistence.inmemory;

import co.edu.uniquindio.sga.domain.entity.MedioPagoAceptado;
import co.edu.uniquindio.sga.domain.repository.MedioPagoRepository;
import co.edu.uniquindio.sga.domain.valueobject.MedioPago;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Adaptador en memoria del catálogo de medios de pago, para las pruebas del dominio. */
public class MedioPagoRepositoryInMemory implements MedioPagoRepository {

    private final Map<MedioPago, MedioPagoAceptado> medios = new LinkedHashMap<>();

    @Override
    public MedioPagoAceptado guardar(MedioPagoAceptado medio) {
        medios.put(medio.getMedio(), medio);
        return medio;
    }

    @Override
    public Optional<MedioPagoAceptado> buscar(MedioPago medio) {
        return Optional.ofNullable(medios.get(medio));
    }

    @Override
    public List<MedioPagoAceptado> listarTodos() {
        return new ArrayList<>(medios.values());
    }
}

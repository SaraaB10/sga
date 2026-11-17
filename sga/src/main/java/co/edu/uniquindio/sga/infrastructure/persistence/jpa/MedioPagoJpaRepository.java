package co.edu.uniquindio.sga.infrastructure.persistence.jpa;

import co.edu.uniquindio.sga.domain.entity.MedioPagoAceptado;
import co.edu.uniquindio.sga.domain.repository.MedioPagoRepository;
import co.edu.uniquindio.sga.domain.valueobject.MedioPago;
import co.edu.uniquindio.sga.infrastructure.persistence.jpa.entity.MedioPagoEntity;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Adaptador JPA del catálogo de medios de pago (L-18). */
public class MedioPagoJpaRepository implements MedioPagoRepository {

    private final MedioPagoJpaDataRepository dataRepository;

    public MedioPagoJpaRepository(MedioPagoJpaDataRepository dataRepository) {
        this.dataRepository = Objects.requireNonNull(dataRepository);
    }

    @Override
    public MedioPagoAceptado guardar(MedioPagoAceptado medio) {
        MedioPagoEntity entidad = dataRepository.findByCodigo(medio.getMedio().codigo()).orElseGet(MedioPagoEntity::new);
        entidad.setCodigo(medio.getMedio().codigo());
        entidad.setNombre(medio.getNombre());
        entidad.setActivo(medio.isActivo());
        return aDominio(dataRepository.save(entidad));
    }

    @Override
    public Optional<MedioPagoAceptado> buscar(MedioPago medio) {
        return medio == null ? Optional.empty() : dataRepository.findByCodigo(medio.codigo()).map(this::aDominio);
    }

    @Override
    public List<MedioPagoAceptado> listarTodos() {
        return dataRepository.findAllByOrderByIdAsc().stream().map(this::aDominio).toList();
    }

    private MedioPagoAceptado aDominio(MedioPagoEntity e) {
        return MedioPagoAceptado.reconstruir(new MedioPago(e.getCodigo()), e.getNombre(), e.isActivo());
    }
}

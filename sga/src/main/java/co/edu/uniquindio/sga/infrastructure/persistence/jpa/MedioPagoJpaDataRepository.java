package co.edu.uniquindio.sga.infrastructure.persistence.jpa;

import co.edu.uniquindio.sga.infrastructure.persistence.jpa.entity.MedioPagoEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MedioPagoJpaDataRepository extends JpaRepository<MedioPagoEntity, Long> {

    Optional<MedioPagoEntity> findByCodigo(String codigo);

    List<MedioPagoEntity> findAllByOrderByIdAsc();
}

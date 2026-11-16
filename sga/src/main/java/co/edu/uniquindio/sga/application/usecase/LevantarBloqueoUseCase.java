package co.edu.uniquindio.sga.application.usecase;

import co.edu.uniquindio.sga.application.exception.ApartamentoNoEncontradoException;
import co.edu.uniquindio.sga.application.exception.BloqueoNoEncontradoException;
import co.edu.uniquindio.sga.domain.entity.Bloqueo;
import co.edu.uniquindio.sga.domain.repository.ApartamentoRepository;
import co.edu.uniquindio.sga.domain.repository.BloqueoRepository;
import co.edu.uniquindio.sga.domain.service.RegistrarBloqueoService;
import co.edu.uniquindio.sga.domain.valueobject.IdentificacionApartamento;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Objects;

/**
 * CU-13. PUT /api/apartamentos/{id}/bloqueos/{idBloqueo}/levantar
 * Levanta un bloqueo vigente: sus noches vuelven a la venta (RN-07). La eliminación es lógica: el bloqueo se conserva
 * como no vigente, con la fecha en que se levantó. Un bloqueo ya levantado no se levanta de nuevo (409).
 * Actor: administrador.
 */
@Service
public class LevantarBloqueoUseCase {

    private final ApartamentoRepository apartamentoRepository;
    private final BloqueoRepository bloqueoRepository;
    private final RegistrarBloqueoService registrarBloqueoService;
    private final Clock reloj;

    public LevantarBloqueoUseCase(ApartamentoRepository apartamentoRepository, BloqueoRepository bloqueoRepository,
                                  RegistrarBloqueoService registrarBloqueoService, Clock reloj) {
        this.apartamentoRepository = Objects.requireNonNull(apartamentoRepository);
        this.bloqueoRepository = Objects.requireNonNull(bloqueoRepository);
        this.registrarBloqueoService = Objects.requireNonNull(registrarBloqueoService);
        this.reloj = Objects.requireNonNull(reloj);
    }

    /** 404 si el apartamento no existe, si el bloqueo no existe o si el bloqueo es de otro apartamento. */
    @Transactional
    public Bloqueo ejecutar(IdentificacionApartamento apartamento, String idBloqueo) {
        if (apartamento == null || !apartamentoRepository.existe(apartamento)) {
            throw new ApartamentoNoEncontradoException(apartamento);
        }
        bloqueoRepository.buscarPorId(idBloqueo)
                .filter(bloqueo -> bloqueo.perteneceA(apartamento))
                .orElseThrow(() -> new BloqueoNoEncontradoException(apartamento, idBloqueo));
        registrarBloqueoService.levantarBloqueo(apartamento, idBloqueo, LocalDate.now(reloj));
        return bloqueoRepository.buscarPorId(idBloqueo)
                .orElseThrow(() -> new BloqueoNoEncontradoException(apartamento, idBloqueo));
    }
}

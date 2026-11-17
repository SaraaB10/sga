package co.edu.uniquindio.sga.application.usecase;

import co.edu.uniquindio.sga.application.exception.ApartamentoNoEncontradoException;
import co.edu.uniquindio.sga.domain.entity.Apartamento;
import co.edu.uniquindio.sga.domain.repository.ApartamentoRepository;
import co.edu.uniquindio.sga.domain.service.DisponibilidadApartamentoService;
import co.edu.uniquindio.sga.domain.valueobject.CodigoReserva;
import co.edu.uniquindio.sga.domain.valueobject.IdentificacionApartamento;
import co.edu.uniquindio.sga.domain.valueobject.NocheDisponibilidad;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * GET /api/apartamentos/{id}/disponibilidad?desde=&hasta=
 * Calendario público de disponibilidad del apartamento: por cada noche dice si está libre o no, sin datos de las
 * reservas. Lo calcula DisponibilidadApartamentoService con las mismas reglas de la búsqueda. Solo lee.
 * Actores: visitante y huésped.
 */
@Service
public class ConsultarDisponibilidadApartamentoUseCase {

    private final ApartamentoRepository apartamentoRepository;
    private final DisponibilidadApartamentoService disponibilidadService;
    private final Clock reloj;

    /** @param reloj reloj con la zona horaria del alojamiento. */
    public ConsultarDisponibilidadApartamentoUseCase(ApartamentoRepository apartamentoRepository,
                                                     DisponibilidadApartamentoService disponibilidadService,
                                                     Clock reloj) {
        this.apartamentoRepository = Objects.requireNonNull(apartamentoRepository);
        this.disponibilidadService = Objects.requireNonNull(disponibilidadService);
        this.reloj = Objects.requireNonNull(reloj);
    }

    /** @param hasta primera noche que ya no se muestra (rango semiabierto). */
    @Transactional(readOnly = true)
    public List<NocheDisponibilidad> ejecutar(IdentificacionApartamento identificacion, LocalDate desde,
                                              LocalDate hasta) {
        return ejecutar(identificacion, desde, hasta, null);
    }

    /**
     * @param reservaExcluida reserva que se está modificando: sus noches no cuentan como ocupadas (RN-14). Puede ser
     *                        null.
     */
    @Transactional(readOnly = true)
    public List<NocheDisponibilidad> ejecutar(IdentificacionApartamento identificacion, LocalDate desde,
                                              LocalDate hasta, CodigoReserva reservaExcluida) {
        Apartamento apartamento = apartamentoRepository.buscarPorIdentificacion(identificacion)
                .filter(encontrado -> !encontrado.isEliminado())
                .orElseThrow(() -> new ApartamentoNoEncontradoException(identificacion));
        return disponibilidadService.disponibilidadPorNoche(apartamento, desde, hasta, LocalDate.now(reloj),
                reservaExcluida);
    }
}

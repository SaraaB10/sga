package co.edu.uniquindio.sga.application.usecase;

import co.edu.uniquindio.sga.application.exception.FolioNoEncontradoException;
import co.edu.uniquindio.sga.application.exception.ReservaNoEncontradaException;
import co.edu.uniquindio.sga.domain.entity.Folio;
import co.edu.uniquindio.sga.domain.entity.MedioPagoAceptado;
import co.edu.uniquindio.sga.domain.entity.Reserva;
import co.edu.uniquindio.sga.domain.exception.ReglaDominioException;
import co.edu.uniquindio.sga.domain.repository.FolioRepository;
import co.edu.uniquindio.sga.domain.repository.MedioPagoRepository;
import co.edu.uniquindio.sga.domain.repository.ReservaRepository;
import co.edu.uniquindio.sga.domain.valueobject.CodigoReserva;
import co.edu.uniquindio.sga.domain.valueobject.EstadoReserva;
import co.edu.uniquindio.sga.domain.valueobject.MedioPago;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Objects;

/**
 * RP-02. POST /api/reservas/{codigo}/folio/devolucion-deposito
 * Devuelve el depósito de garantía durante la estancia, antes de la salida: asienta el movimiento inverso del cargo
 * del depósito y registra la devolución del dinero que el huésped ya había pagado, con su medio y la fecha de hoy.
 * El sistema registra la devolución; el desembolso ocurre fuera (3.4). Así el folio puede cerrarse en cero (RN-17).
 * Solo para reservas EN_CURSO: en una cancelación o un no-show el depósito ya se anula con los demás cargos.
 * Actor: recepcionista o administrador.
 */
@Service
public class DevolverDepositoUseCase {

    private final ReservaRepository reservaRepository;
    private final FolioRepository folioRepository;
    private final MedioPagoRepository medioPagoRepository;
    private final Clock reloj;

    public DevolverDepositoUseCase(ReservaRepository reservaRepository, FolioRepository folioRepository,
                                   MedioPagoRepository medioPagoRepository, Clock reloj) {
        this.reservaRepository = Objects.requireNonNull(reservaRepository);
        this.folioRepository = Objects.requireNonNull(folioRepository);
        this.medioPagoRepository = Objects.requireNonNull(medioPagoRepository);
        this.reloj = Objects.requireNonNull(reloj);
    }

    /** @return el folio con la devolución registrada, para mostrar el nuevo saldo. */
    @Transactional
    public Folio ejecutar(CodigoReserva codigo, MedioPago medio) {
        Reserva reserva = codigo == null ? null : reservaRepository.buscarPorCodigo(codigo).orElse(null);
        if (reserva == null) {
            throw new ReservaNoEncontradaException(codigo);
        }
        if (reserva.getEstado() != EstadoReserva.EN_CURSO) {
            throw new ReglaDominioException("RP-02: el depósito se devuelve durante la estancia, antes de la salida; "
                    + "la reserva " + codigo.valor() + " está " + reserva.getEstado());
        }
        Folio folio = folioRepository.buscarPorReserva(codigo)
                .orElseThrow(() -> new FolioNoEncontradoException(codigo));
        // L-18: la devolución se registra con un medio del catálogo que siga activo
        boolean aceptado = medioPagoRepository.buscar(medio).map(MedioPagoAceptado::isActivo).orElse(false);
        if (!aceptado) {
            throw new ReglaDominioException("L-18: el medio de pago " + medio + " no está entre los medios aceptados");
        }
        folio.devolverDeposito(medio, LocalDate.now(reloj));
        return folioRepository.guardar(folio);
    }
}

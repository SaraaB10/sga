package co.edu.uniquindio.sga.application.usecase;

import co.edu.uniquindio.sga.domain.entity.MedioPagoAceptado;
import co.edu.uniquindio.sga.domain.repository.MedioPagoRepository;
import co.edu.uniquindio.sga.domain.service.ReportesService;
import co.edu.uniquindio.sga.domain.valueobject.ReporteIngresos;
import co.edu.uniquindio.sga.domain.valueobject.ReporteOcupacion;
import co.edu.uniquindio.sga.domain.valueobject.ReporteReservasPorCanal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Objects;

/**
 * GET /api/reportes/ocupacion, /api/reportes/ingresos y /api/reportes/reservas-por-canal?desde=&hasta=
 * Reportes del periodo (7.9), con desde y hasta incluidas. El cálculo lo hace ReportesService; aquí solo se abre
 * una transacción de lectura. Actor: administrador.
 */
@Service
public class ConsultarReportesUseCase {

    private final ReportesService reportesService;
    private final MedioPagoRepository medioPagoRepository;

    public ConsultarReportesUseCase(ReportesService reportesService, MedioPagoRepository medioPagoRepository) {
        this.reportesService = Objects.requireNonNull(reportesService);
        this.medioPagoRepository = Objects.requireNonNull(medioPagoRepository);
    }

    @Transactional(readOnly = true)
    public ReporteOcupacion ocupacion(LocalDate desde, LocalDate hasta) {
        return reportesService.ocupacion(desde, hasta);
    }

    @Transactional(readOnly = true)
    public ReporteIngresos ingresos(LocalDate desde, LocalDate hasta) {
        return reportesService.ingresos(desde, hasta,
                medioPagoRepository.listarTodos().stream().map(MedioPagoAceptado::getMedio).toList());
    }

    @Transactional(readOnly = true)
    public ReporteReservasPorCanal reservasPorCanal(LocalDate desde, LocalDate hasta) {
        return reportesService.reservasPorCanal(desde, hasta);
    }
}

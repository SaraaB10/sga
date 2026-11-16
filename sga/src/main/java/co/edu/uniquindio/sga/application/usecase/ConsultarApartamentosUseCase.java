package co.edu.uniquindio.sga.application.usecase;

import co.edu.uniquindio.sga.application.exception.ApartamentoNoEncontradoException;
import co.edu.uniquindio.sga.domain.entity.Apartamento;
import co.edu.uniquindio.sga.domain.repository.ApartamentoRepository;
import co.edu.uniquindio.sga.domain.repository.Pagina;
import co.edu.uniquindio.sga.domain.valueobject.IdentificacionApartamento;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Collator;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * CU-20. GET /api/apartamentos?pagina= y GET /api/apartamentos/{id}
 * Consulta los apartamentos del alojamiento para la administración (7.3). Los eliminados lógicamente no salen
 * en el listado ni en el detalle (12.4). Solo consulta: no modifica datos.
 * Actor: administrador.
 *
 * CU-35 (7.1). GET /api/apartamentos/pendientes-preparacion?pagina=
 * Apartamentos que el personal de servicio debe atender (PENDIENTE_PREPARACION o EN_PREPARACION).
 * Actor: personal de servicio.
 */

@Service
public class ConsultarApartamentosUseCase {

    private final ApartamentoRepository apartamentoRepository;
    private final int tamanoPagina;

    public ConsultarApartamentosUseCase(ApartamentoRepository apartamentoRepository,
                                        @Value("${sga.paginacion.tamano}") int tamanoPagina) {
        this.apartamentoRepository = Objects.requireNonNull(apartamentoRepository);
        this.tamanoPagina = tamanoPagina;
    }

    @Transactional(readOnly = true)
    public Pagina<Apartamento> listar(int pagina) {
        return apartamentoRepository.listarNoEliminados(pagina, tamanoPagina);
    }

    /** CU-35 (7.1): apartamentos no eliminados en PENDIENTE_PREPARACION o EN_PREPARACION, por identificación. */
    @Transactional(readOnly = true)
    public Pagina<Apartamento> pendientesDePreparacion(int pagina) {
        return apartamentoRepository.listarPendientesDePreparacion(pagina, tamanoPagina);
    }

    /** Opciones de los filtros de la búsqueda, con los valores reales de los apartamentos activos */
    public record OpcionesFiltro(List<String> caracteristicas, List<String> servicios) {
    }

    /**
     * 7.5: las características (por ejemplo "2 dormitorios") y los elementos de la dotación de los apartamentos que
     * están a la venta, sin repetir y en orden alfabético (las características, por su número).
     */
    @Transactional(readOnly = true)
    public OpcionesFiltro opcionesDeFiltro() {
        List<Apartamento> activos = apartamentoRepository.listarVigentesParaVenta();
        List<String> caracteristicas = activos.stream()
                .sorted(Comparator.comparingInt(Apartamento::getDormitorios))
                .flatMap(a -> a.getCaracteristicas().stream())
                .distinct()
                .toList();
        Collator orden = Collator.getInstance(Locale.forLanguageTag("es-CO"));
        orden.setStrength(Collator.SECONDARY);                           // sin distinguir mayúsculas
        Map<String, String> servicios = new TreeMap<>(orden);           // sin repetir aunque cambie la mayúscula
        activos.forEach(a -> a.getDotacion().elementos().forEach(e -> servicios.putIfAbsent(e.trim(), e.trim())));
        return new OpcionesFiltro(caracteristicas, List.copyOf(servicios.values()));
    }

    /** 404 si no existe o si fue retirado de la venta (eliminado lógicamente). */
    @Transactional(readOnly = true)
    public Apartamento detalle(IdentificacionApartamento identificacion) {
        return apartamentoRepository.buscarPorIdentificacion(identificacion)
                .filter(apartamento -> !apartamento.isEliminado())
                .orElseThrow(() -> new ApartamentoNoEncontradoException(identificacion));
    }
}

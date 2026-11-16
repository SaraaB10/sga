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
    public record OpcionesFiltro(List<Integer> dormitorios, List<String> caracteristicas, List<String> servicios) {
    }

    /**
     * 7.5: los números de dormitorios, las características (por ejemplo "Vista al mar") y los elementos de la
     * dotación de los apartamentos que están a la venta, sin repetir; los textos en orden alfabético.
     */
    @Transactional(readOnly = true)
    public OpcionesFiltro opcionesDeFiltro() {
        List<Apartamento> activos = apartamentoRepository.listarVigentesParaVenta();
        List<Integer> dormitorios = activos.stream()
                .map(Apartamento::getDormitorios)
                .distinct()
                .sorted(Comparator.naturalOrder())
                .toList();
        return new OpcionesFiltro(dormitorios,
                sinRepetir(activos.stream().flatMap(a -> a.getCaracteristicas().elementos().stream()).toList()),
                sinRepetir(activos.stream().flatMap(a -> a.getDotacion().elementos().stream()).toList()));
    }

    /** Sin repetir aunque cambie la mayúscula, en orden alfabético del español */
    private static List<String> sinRepetir(List<String> textos) {
        Collator orden = Collator.getInstance(Locale.forLanguageTag("es-CO"));
        orden.setStrength(Collator.SECONDARY);
        Map<String, String> unicos = new TreeMap<>(orden);
        textos.forEach(t -> unicos.putIfAbsent(t.trim(), t.trim()));
        return List.copyOf(unicos.values());
    }

    /** 404 si no existe o si fue retirado de la venta (eliminado lógicamente). */
    @Transactional(readOnly = true)
    public Apartamento detalle(IdentificacionApartamento identificacion) {
        return apartamentoRepository.buscarPorIdentificacion(identificacion)
                .filter(apartamento -> !apartamento.isEliminado())
                .orElseThrow(() -> new ApartamentoNoEncontradoException(identificacion));
    }
}

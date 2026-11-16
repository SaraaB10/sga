package co.edu.uniquindio.sga.domain.service;

import co.edu.uniquindio.sga.domain.entity.Alojamiento;
import co.edu.uniquindio.sga.domain.entity.Apartamento;
import co.edu.uniquindio.sga.domain.entity.Reserva;
import co.edu.uniquindio.sga.domain.exception.ReglaDominioException;
import co.edu.uniquindio.sga.domain.repository.AlojamientoRepository;
import co.edu.uniquindio.sga.domain.repository.ApartamentoRepository;
import co.edu.uniquindio.sga.domain.repository.BloqueoRepository;
import co.edu.uniquindio.sga.domain.repository.ReservaRepository;
import co.edu.uniquindio.sga.domain.valueobject.ApartamentoDisponible;
import co.edu.uniquindio.sga.domain.valueobject.CodigoReserva;
import co.edu.uniquindio.sga.domain.valueobject.Coordenada;
import co.edu.uniquindio.sga.domain.valueobject.CriterioBusqueda;
import co.edu.uniquindio.sga.domain.valueobject.Estancia;
import co.edu.uniquindio.sga.domain.valueobject.NocheDisponibilidad;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Responde si un apartamento puede venderse para una estancia (3.3). Es el único lugar donde se cruzan
 * las reservas de todos los canales, porque ninguna entidad por sí sola conoce las demás reservas.
 *
 * Reglas: RN-01 (sin reservas activas solapadas, sin importar el canal), RN-02 (capacidad),
 * RN-07 (bloqueos), RN-20 (tiempo de preparación entre una salida y una entrada el mismo día),
 * RP-01 (estancia mínima de fin de semana).
 */
public class DisponibilidadApartamentoService {

    private final ReservaRepository reservaRepository;
    private final ApartamentoRepository apartamentoRepository;
    private final BloqueoRepository bloqueoRepository;
    private final AlojamientoRepository alojamientoRepository;
    private final CotizadorEstanciaService cotizador;

    public DisponibilidadApartamentoService(ReservaRepository reservaRepository,
                                            ApartamentoRepository apartamentoRepository,
                                            BloqueoRepository bloqueoRepository,
                                            AlojamientoRepository alojamientoRepository,
                                            CotizadorEstanciaService cotizador) {
        this.reservaRepository = Objects.requireNonNull(reservaRepository);
        this.apartamentoRepository = Objects.requireNonNull(apartamentoRepository);
        this.bloqueoRepository = Objects.requireNonNull(bloqueoRepository);
        this.alojamientoRepository = Objects.requireNonNull(alojamientoRepository);
        this.cotizador = Objects.requireNonNull(cotizador);
    }

    /** Arma su propio cotizador con los mismos repositorios; lo usan las pruebas que no lo necesitan aparte. */
    public DisponibilidadApartamentoService(ReservaRepository reservaRepository,
                                            ApartamentoRepository apartamentoRepository,
                                            BloqueoRepository bloqueoRepository,
                                            AlojamientoRepository alojamientoRepository) {
        this(reservaRepository, apartamentoRepository, bloqueoRepository, alojamientoRepository,
                new CotizadorEstanciaService(apartamentoRepository, alojamientoRepository));
    }

    /** F-06: apartamentos vendibles para la estancia y el tamaño del grupo. Lo usa también el canal externo. */
    public List<Apartamento> buscarDisponibles(Estancia estancia, int totalOcupantes) {
        requerido(estancia, "La estancia es obligatoria");
        obtenerAlojamiento().validarEstanciaMinima(estancia);
        return apartamentoRepository.listarVigentesParaVenta().stream()
                .filter(apartamento -> estaDisponible(apartamento, estancia, totalOcupantes, null))
                .toList();
    }

    /**
     * CU-01 con filtros (7.5). Primero decide la disponibilidad igual que {@link #buscarDisponibles(Estancia, int)}
     * (RN-01, RN-02, RN-07, RN-20, RP-01); sobre ese resultado aplica los filtros del criterio: capacidad mínima,
     * característica de la dotación (sin distinguir mayúsculas) y rango de precio del valor estimado de la estancia.
     * El valor estimado lo calcula CotizadorEstanciaService (RN-05, RN-06); si no llegan fechas de nacimiento,
     * todos los ocupantes cuentan como facturables (ver CotizadorEstanciaService.estimar).
     *
     * @param fechasNacimiento opcional (null o vacía); si trae datos debe haber una por ocupante.
     * @param criterio         opcional; null equivale a {@link CriterioBusqueda#sinFiltros()}.
     * @return disponibles ordenados por identificación, con su valor estimado.
     */
    public List<ApartamentoDisponible> buscarDisponibles(Estancia estancia, int totalOcupantes,
                                                         List<LocalDate> fechasNacimiento,
                                                         CriterioBusqueda criterio) {
        CriterioBusqueda filtros = criterio == null ? CriterioBusqueda.sinFiltros() : criterio;
        // El grupo debe tener al menos un ocupante facturable, aunque no haya apartamentos disponibles
        cotizador.validarGrupoConFacturable(estancia, fechasNacimiento);
        Coordenada ubicacion = obtenerAlojamiento().getUbicacion();
        return buscarDisponibles(estancia, totalOcupantes).stream()
                .filter(apartamento -> filtros.cumpleCapacidad(apartamento.getCapacidad()))
                .filter(apartamento -> filtros.cumpleDormitorios(apartamento.getDormitorios()))
                .filter(apartamento -> filtros.cumpleCaracteristica(apartamento.getCaracteristicas()))
                .filter(apartamento -> filtros.cumpleServicios(apartamento.getDotacion()))
                .map(apartamento -> new ApartamentoDisponible(apartamento,
                        cotizador.estimar(apartamento, estancia, totalOcupantes, fechasNacimiento).valorTotal(),
                        ubicacion))
                .filter(disponible -> filtros.cumplePrecio(disponible.valorEstimado()))
                .toList();
    }

    /**
     * Calendario público de disponibilidad: para cada noche de [desde, hasta) dice si se puede vender una estancia
     * de esa sola noche, con las mismas reglas de {@link #estaDisponible} (apartamento activo, sin bloqueos ni
     * reservas activas, RN-20). Las noches anteriores a hoy nunca están libres (RN-04). Abarca como máximo
     * {@link CalendarioOcupacionService#MAXIMO_NOCHES} noches.
     *
     * @param hasta primera noche que ya no se muestra (excluida).
     * @param hoy   fecha actual en la zona horaria del alojamiento.
     */
    public List<NocheDisponibilidad> disponibilidadPorNoche(Apartamento apartamento, LocalDate desde, LocalDate hasta,
                                                            LocalDate hoy) {
        return disponibilidadPorNoche(apartamento, desde, hasta, hoy, null);
    }

    /**
     * Igual que {@link #disponibilidadPorNoche(Apartamento, LocalDate, LocalDate, LocalDate)}, sin contar como
     * ocupación la reserva que se está modificando (RN-14): sus noches y su tiempo de preparación quedan libres.
     *
     * @param reservaExcluida reserva que no se cuenta como ocupación; puede ser null.
     */
    public List<NocheDisponibilidad> disponibilidadPorNoche(Apartamento apartamento, LocalDate desde, LocalDate hasta,
                                                            LocalDate hoy, CodigoReserva reservaExcluida) {
        requerido(apartamento, "El apartamento es obligatorio");
        requerido(hoy, "La fecha actual es obligatoria");
        if (desde == null || hasta == null) {
            throw new ReglaDominioException("La disponibilidad debe tener fecha desde y fecha hasta");
        }
        if (!desde.isBefore(hasta)) {
            throw new ReglaDominioException("La fecha desde debe ser anterior a la fecha hasta");
        }
        long noches = ChronoUnit.DAYS.between(desde, hasta);
        if (noches > CalendarioOcupacionService.MAXIMO_NOCHES) {
            throw new ReglaDominioException("La disponibilidad abarca máximo " + CalendarioOcupacionService.MAXIMO_NOCHES
                    + " noches y se pidieron " + noches);
        }
        return new Estancia(desde, hasta).fechasDeNoches().stream()
                .map(noche -> {
                    Estancia unaNoche = new Estancia(noche, noche.plusDays(1));
                    // RN-07: se marca la noche bloqueada (también si ya pasó, para que el calendario lo muestre)
                    boolean bloqueada = !bloqueoRepository
                            .listarVigentesQueSeSolapan(apartamento.getIdentificacion(), unaNoche).isEmpty();
                    boolean libre = !noche.isBefore(hoy) && !bloqueada
                            && estaDisponible(apartamento, unaNoche, 1, reservaExcluida);
                    return new NocheDisponibilidad(noche, libre, bloqueada);
                })
                .toList();
    }

    /**
     * @param reservaExcluida reserva que no se cuenta como ocupación (la que se está modificando); puede ser null.
     */
    public boolean estaDisponible(Apartamento apartamento, Estancia estancia, int totalOcupantes,
                                  CodigoReserva reservaExcluida) {
        requerido(apartamento, "El apartamento es obligatorio");
        requerido(estancia, "La estancia es obligatoria");
        return apartamento.estaVigenteParaVenta()
                && apartamento.admite(totalOcupantes)
                && conflictoDeInventario(apartamento, estancia, reservaExcluida).isEmpty();
    }

    /**
     * Las seis condiciones para crear o modificar una reserva, siempre en este orden, de modo que una solicitud
     * que viola varias falla por la primera:
     * <ol>
     *     <li>RN-03: la fecha de salida es posterior a la de entrada.</li>
     *     <li>RN-04: la fecha de entrada no es anterior a hoy.</li>
     *     <li>El apartamento está activo y tiene tarifa en todas las temporadas.</li>
     *     <li>RN-02: la capacidad alcanza para el total de ocupantes.</li>
     *     <li>RN-07 y RN-01: no hay bloqueos ni reservas activas en esas noches.</li>
     *     <li>RN-20: se respeta el tiempo de preparación respecto de la estancia anterior y la siguiente.</li>
     * </ol>
     * Después de las seis se aplica RP-01 (estancia mínima de fin de semana).
     * Lo usan CreacionReservaService y ModificacionReservaService (RN-14) antes de tocar la reserva.
     *
     * @param reservaExcluida reserva que no se cuenta como ocupación (la que se está modificando); puede ser null.
     * @param hoy             fecha actual en la zona horaria del alojamiento.
     */
    public void validarCondicionesDeReserva(Apartamento apartamento, Estancia estancia, int totalOcupantes,
                                            CodigoReserva reservaExcluida, LocalDate hoy) {
        requerido(apartamento, "El apartamento es obligatorio");
        requerido(estancia, "La estancia es obligatoria");
        requerido(hoy, "La fecha actual es obligatoria");
        // 1. RN-03
        if (estancia.noches() < 1) {
            throw new ReglaDominioException("RN-03: La fecha de salida debe ser posterior a la de entrada");
        }
        // 2. RN-04
        if (estancia.fechaEntrada().isBefore(hoy)) {
            throw new ReglaDominioException("RN-04: La fecha de entrada " + estancia.fechaEntrada()
                    + " es anterior a la fecha actual " + hoy);
        }
        validarDisponibilidad(apartamento, estancia, totalOcupantes, reservaExcluida);
    }

    /**
     * Condiciones 3 a 6 de {@link #validarCondicionesDeReserva} (apartamento activo con tarifas completas,
     * capacidad, bloqueos y solapamiento, tiempo de preparación) y después RP-01. Lanza la excepción con la
     * primera regla que falla. Lo usa también el canal externo, que valida las fechas al armar el mensaje.
     */
    public void validarDisponibilidad(Apartamento apartamento, Estancia estancia, int totalOcupantes,
                                      CodigoReserva reservaExcluida) {
        requerido(apartamento, "El apartamento es obligatorio");
        requerido(estancia, "La estancia es obligatoria");
        Alojamiento alojamiento = obtenerAlojamiento();
        String codigo = apartamento.getIdentificacion().codigo();
        // 3. Apartamento activo y con tarifas completas
        if (!apartamento.estaVigenteParaVenta()) {
            throw new ReglaDominioException("3.3: El apartamento " + codigo + " no está activo para la venta");
        }
        if (!alojamiento.getTemporadas().isEmpty() && !apartamento.tieneTarifasCompletas(alojamiento.getTemporadas())) {
            throw new ReglaDominioException("7.4: El apartamento " + codigo + " no tiene tarifa en todas las temporadas");
        }
        // 4. RN-02
        apartamento.validarCapacidadPara(totalOcupantes);
        // 5 (RN-07 y RN-01) y 6 (RN-20)
        conflictoDeInventario(apartamento, estancia, reservaExcluida).ifPresent(motivo -> {
            throw new ReglaDominioException(motivo);
        });
        // Regla propia que se aplica después de las seis condiciones
        alojamiento.validarEstanciaMinima(estancia);
    }

    /**
     * Motivo por el que las noches ya no están libres (RN-07, RN-01 o RN-20), o vacío si están libres.
     * ConciliacionCanalExternoService lo usa para distinguir un conflicto de inventario (RN-18)
     * de un mensaje con datos inválidos.
     */
    public Optional<String> conflictoDeInventario(Apartamento apartamento, Estancia estancia,
                                                  CodigoReserva reservaExcluida) {
        String codigo = apartamento.getIdentificacion().codigo();

        boolean bloqueado = apartamento.estaBloqueadoDurante(estancia)
                || !bloqueoRepository.listarVigentesQueSeSolapan(apartamento.getIdentificacion(), estancia).isEmpty();
        if (bloqueado) {
            return Optional.of("RN-07: El apartamento " + codigo + " tiene un bloqueo en esas noches");
        }

        List<Reserva> otrasActivas = reservaRepository.listarActivasPorApartamento(apartamento.getIdentificacion())
                .stream()
                .filter(reserva -> !reserva.getCodigo().equals(reservaExcluida))
                .toList();

        Optional<Reserva> solapada = otrasActivas.stream().filter(r -> r.ocupaNochesDe(estancia)).findFirst();
        if (solapada.isPresent()) {
            return Optional.of("RN-01: El apartamento " + codigo + " ya tiene la reserva "
                    + solapada.get().getCodigo().valor() + " en esas noches");
        }

        if (!obtenerAlojamiento().permiteEntradaElMismoDiaDeUnaSalida()) {
            boolean pegadaAOtra = otrasActivas.stream().anyMatch(r ->
                    r.getEstancia().fechaSalida().equals(estancia.fechaEntrada())
                            || r.getEstancia().fechaEntrada().equals(estancia.fechaSalida()));
            if (pegadaAOtra) {
                return Optional.of("RN-20: El apartamento " + codigo + " no alcanza a prepararse entre una salida "
                        + "y una entrada el mismo día");
            }
        }
        return Optional.empty();
    }

    private Alojamiento obtenerAlojamiento() {
        return alojamientoRepository.obtener()
                .orElseThrow(() -> new ReglaDominioException("F-13: No hay un alojamiento configurado"));
    }

    private static void requerido(Object valor, String mensaje) {
        if (valor == null) throw new ReglaDominioException(mensaje);
    }
}

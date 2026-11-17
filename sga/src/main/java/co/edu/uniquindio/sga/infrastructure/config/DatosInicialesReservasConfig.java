package co.edu.uniquindio.sga.infrastructure.config;

import co.edu.uniquindio.sga.domain.entity.Alojamiento;
import co.edu.uniquindio.sga.domain.entity.Apartamento;
import co.edu.uniquindio.sga.domain.entity.Folio;
import co.edu.uniquindio.sga.domain.entity.Reserva;
import co.edu.uniquindio.sga.domain.exception.ReglaDominioException;
import co.edu.uniquindio.sga.domain.repository.AlojamientoRepository;
import co.edu.uniquindio.sga.domain.repository.ApartamentoRepository;
import co.edu.uniquindio.sga.domain.repository.FolioRepository;
import co.edu.uniquindio.sga.domain.repository.ReservaRepository;
import co.edu.uniquindio.sga.domain.service.CancelarReservaService;
import co.edu.uniquindio.sga.domain.service.ConfirmacionReservaService;
import co.edu.uniquindio.sga.domain.service.CotizadorEstanciaService;
import co.edu.uniquindio.sga.domain.service.CreacionReservaService;
import co.edu.uniquindio.sga.domain.service.PoliticaCancelacionService;
import co.edu.uniquindio.sga.domain.valueobject.CanalOrigen;
import co.edu.uniquindio.sga.domain.valueobject.Cargo;
import co.edu.uniquindio.sga.domain.valueobject.CodigoReserva;
import co.edu.uniquindio.sga.domain.valueobject.Cotizacion;
import co.edu.uniquindio.sga.domain.valueobject.DepositoGarantia;
import co.edu.uniquindio.sga.domain.valueobject.Dinero;
import co.edu.uniquindio.sga.domain.valueobject.DocumentoIdentidad;
import co.edu.uniquindio.sga.domain.valueobject.EstadoOperativo;
import co.edu.uniquindio.sga.domain.valueobject.EstadoReserva;
import co.edu.uniquindio.sga.domain.valueobject.Estancia;
import co.edu.uniquindio.sga.domain.valueobject.IdentificacionApartamento;
import co.edu.uniquindio.sga.domain.valueobject.Mascotas;
import co.edu.uniquindio.sga.domain.valueobject.MedioPago;
import co.edu.uniquindio.sga.domain.valueobject.Ocupante;
import co.edu.uniquindio.sga.domain.valueobject.Pago;
import co.edu.uniquindio.sga.domain.valueobject.PoliticaCancelacion;
import co.edu.uniquindio.sga.domain.valueobject.TipoCargo;
import co.edu.uniquindio.sga.domain.valueobject.Titular;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

/**
 * Datos de prueba de reservas (12.3): al menos una reserva en cada estado del ciclo de vida (sección 8) y al menos
 * una por cada canal de origen. Corre después de los datos del alojamiento (@Order(2)). Si ya hay reservas no
 * duplica nada: solo agrega una por cada estado o canal que no tenga ninguna, en noches libres (Cargador.completar).
 * Las fechas son relativas a hoy para que la demostración funcione cualquier día.
 *
 * Solo usa APT-101, APT-102 y APT-201 (APT-202 queda fuera de servicio y los bloqueos de APT-101, de hoy+4 a hoy+7,
 * y de APT-301 no se cruzan con ellas) y ninguna estancia retenida se solapa con otra del mismo apartamento
 * (RN-01, RN-20):
 *   APT-101  FINALIZADA  hoy-10 → hoy-7   DIRECTO   (reconstruida: estancia pasada)
 *            PENDIENTE   hoy+10 → hoy+12  PORTAL
 *            CANCELADA   hoy+20 → hoy+22  PORTAL
 *   APT-102  NO_SHOW     hoy-5  → hoy-3   PORTAL    (reconstruida: estancia pasada)
 *            CONFIRMADA  hoy+12 → hoy+15  DIRECTO
 *   APT-201  EN_CURSO    hoy-1  → hoy+2   DIRECTO   (reconstruida: entrada ayer; deja el apartamento OCUPADO)
 *            PENDIENTE   hoy+15 → hoy+18  EXTERNO   (con identificador externo, RN-19)
 *
 * Las reservas futuras nacen con CreacionReservaService (mismas reglas que CU-03, con su folio, F-08) y avanzan
 * con ConfirmacionReservaService y CancelarReservaService. Las de fechas pasadas no pueden crearse con
 * Reserva.crear (RN-04), así que parten de Reserva.reconstruir en CONFIRMADA, igual que el mapper de persistencia,
 * y desde ahí avanzan con los métodos de negocio del agregado (registrarLlegada, registrarSalida, declararNoShow).
 */
@Configuration
public class DatosInicialesReservasConfig {

    private static final Logger log = LoggerFactory.getLogger(DatosInicialesReservasConfig.class);

    private static final LocalTime HORA_LLEGADA = LocalTime.of(16, 0);

    /*
     * Las titulares de la PENDIENTE del portal y del NO_SHOW son las huéspedes con cuenta de application.properties
     * (sga.usuarios-iniciales: huesped1 y huesped2): mismo nombre, documento, correo y fecha de nacimiento, para que
     * cada una vea su reserva en "Mis reservas". Si cambian esos usuarios, deben cambiar también aquí.
     */
    private static final String LAURA_NOMBRE = "Laura Gomez";
    private static final String LAURA_DOCUMENTO = "1094777888";
    private static final String VALENTINA_NOMBRE = "Valentina Rios";
    private static final String VALENTINA_DOCUMENTO = "1094333444";

    /** Fecha de nacimiento de cada titular por su documento; los demás titulares de prueba nacieron el 12-04-1988. */
    private static final Map<String, LocalDate> NACIMIENTO_POR_DOCUMENTO = Map.of(
            LAURA_DOCUMENTO, LocalDate.of(1995, 1, 30),
            VALENTINA_DOCUMENTO, LocalDate.of(1988, 9, 12));
    private static final LocalDate NACIMIENTO_POR_DEFECTO = LocalDate.of(1988, 4, 12);

    /**
     * @param cargarPoliticaCancelacion el runner de la política de cancelación. Se invoca primero porque cada
     *                                  reserva congela la versión vigente (RN-22); es idempotente, así que cuando ya
     *                                  corrió (@Order(0)) la llamada no hace nada.
     * @param cargarParametrosAlojamiento el runner de los parámetros, que antes carga el alojamiento. Se invoca
     *                         primero porque sin alojamiento, temporadas, tarifas y parámetros (umbral, depósito)
     *                         no hay reservas posibles (F-13); es idempotente, así que cuando ya corrió (@Order(1))
     *                         la llamada no hace nada.
     */
    @Bean
    @Order(2)
    public CommandLineRunner cargarReservasDePrueba(
            @Qualifier("cargarPoliticaCancelacion") CommandLineRunner cargarPoliticaCancelacion,
            @Qualifier("cargarParametrosAlojamiento") CommandLineRunner cargarParametrosAlojamiento,
            ReservaRepository reservaRepository,
            FolioRepository folioRepository,
            ApartamentoRepository apartamentoRepository,
            AlojamientoRepository alojamientoRepository,
            CreacionReservaService creacionReservaService,
            ConfirmacionReservaService confirmacionReservaService,
            CancelarReservaService cancelarReservaService,
            CotizadorEstanciaService cotizadorEstanciaService,
            PoliticaCancelacionService politicaCancelacionService,
            Clock reloj) {
        return args -> {
            boolean vacia = reservaRepository.listarTodas().isEmpty();
            cargarPoliticaCancelacion.run(args);
            cargarParametrosAlojamiento.run(args);
            Alojamiento coral = alojamientoRepository.obtener()
                    .orElseThrow(() -> new ReglaDominioException("F-13: No hay un alojamiento configurado"));

            Cargador cargador = new Cargador(coral, reservaRepository, folioRepository, apartamentoRepository,
                    creacionReservaService, confirmacionReservaService, cancelarReservaService,
                    cotizadorEstanciaService, politicaCancelacionService.vigente(), coral.getDepositoGarantia(),
                    LocalDateTime.now(reloj));
            if (vacia) {
                cargador.cargar();
                log.info("Datos de prueba de reservas cargados: {} reservas", reservaRepository.listarTodas().size());
                return;
            }
            // Ya hay reservas: no se duplican; solo se agregan las de los estados y canales que falten
            int creadas = cargador.completar();
            if (creadas > 0) {
                log.info("Datos de prueba de reservas completados: {} reservas nuevas", creadas);
            }
        };
    }

    /** Agrupa las dependencias para no repetirlas en cada reserva de prueba. */
    private record Cargador(Alojamiento coral, ReservaRepository reservas, FolioRepository folios,
                            ApartamentoRepository apartamentos, CreacionReservaService creacion,
                            ConfirmacionReservaService confirmacion, CancelarReservaService cancelacion,
                            CotizadorEstanciaService cotizador, PoliticaCancelacion politicaVigente,
                            DepositoGarantia depositoGarantia, LocalDateTime ahora) {

        void cargar() {
            LocalDate hoy = ahora.toLocalDate();

            // ---------- Estancias pasadas: parten de una reserva CONFIRMADA reconstruida ----------
            finalizada("APT-101", new Estancia(hoy.minusDays(10), hoy.minusDays(7)));
            noShow("APT-102", new Estancia(hoy.minusDays(5), hoy.minusDays(3)));
            enCurso("APT-201", new Estancia(hoy.minusDays(1), hoy.plusDays(2)));

            // ---------- Estancias futuras: nacen PENDIENTE con CreacionReservaService (CU-03) ----------
            pendiente("APT-101", new Estancia(hoy.plusDays(10), hoy.plusDays(12)), CanalOrigen.PORTAL);
            confirmada("APT-102", new Estancia(hoy.plusDays(12), hoy.plusDays(15)));
            cancelada("APT-101", new Estancia(hoy.plusDays(20), hoy.plusDays(22)));
            pendiente("APT-201", new Estancia(hoy.plusDays(15), hoy.plusDays(18)), CanalOrigen.EXTERNO);
        }

        /**
         * Completa una base que ya tiene reservas sin duplicar nada: agrega una reserva por cada estado y por cada
         * canal que no tenga ninguna. Cada una va en el primer apartamento a la venta y las primeras noches libres
         * (sin reservas que no estén canceladas ni bloqueos), y pasa por las mismas reglas que la carga inicial;
         * si un intento choca con una regla, prueba el siguiente.
         *
         * @return cuántas reservas creó.
         */
        int completar() {
            LocalDate hoy = ahora.toLocalDate();
            int antes = reservas.listarTodas().size();
            Set<EstadoReserva> estados = EnumSet.noneOf(EstadoReserva.class);
            reservas.listarTodas().forEach(r -> estados.add(r.getEstado()));

            if (!estados.contains(EstadoReserva.FINALIZADA)) {
                intentar(hoy.minusDays(10), 3, -7, a -> true, this::finalizada);
            }
            if (!estados.contains(EstadoReserva.NO_SHOW)) {
                intentar(hoy.minusDays(5), 2, -7, a -> true, this::noShow);
            }
            if (!estados.contains(EstadoReserva.EN_CURSO)) {
                // Solo un apartamento PREPARADO puede recibir al grupo (RN-11)
                intentar(hoy.minusDays(1), 3, 0,
                        a -> a.getEstadoOperativo() == EstadoOperativo.PREPARADO, this::enCurso);
            }
            if (!estados.contains(EstadoReserva.PENDIENTE)) {
                intentar(hoy.plusDays(10), 2, 7, this::sinFueraDeServicio,
                        (apto, estancia) -> pendiente(apto, estancia, CanalOrigen.PORTAL));
            }
            if (!estados.contains(EstadoReserva.CONFIRMADA)) {
                intentar(hoy.plusDays(12), 3, 7, this::sinFueraDeServicio, this::confirmada);
            }
            if (!estados.contains(EstadoReserva.CANCELADA)) {
                intentar(hoy.plusDays(20), 2, 7, this::sinFueraDeServicio, this::cancelada);
            }

            Set<CanalOrigen> canales = EnumSet.noneOf(CanalOrigen.class);
            reservas.listarTodas().forEach(r -> canales.add(r.getCanalOrigen()));
            for (CanalOrigen canal : CanalOrigen.values()) {
                if (!canales.contains(canal)) {
                    intentar(hoy.plusDays(30), 2, 7, this::sinFueraDeServicio,
                            (apto, estancia) -> pendiente(apto, estancia, canal));
                }
            }
            return reservas.listarTodas().size() - antes;
        }

        private boolean sinFueraDeServicio(Apartamento apartamento) {
            return apartamento.getEstadoOperativo() != EstadoOperativo.FUERA_DE_SERVICIO;
        }

        /**
         * Prueba la estancia en cada apartamento a la venta que cumpla el filtro; si ninguno sirve, la corre
         * {@code paso} días (hasta 12 veces; con paso 0 solo prueba esas fechas).
         */
        private void intentar(LocalDate entrada, int noches, int paso, Predicate<Apartamento> filtro,
                              BiConsumer<String, Estancia> crear) {
            int intentos = paso == 0 ? 1 : 12;
            for (int i = 0; i < intentos; i++) {
                LocalDate desde = entrada.plusDays((long) paso * i);
                Estancia estancia = new Estancia(desde, desde.plusDays(noches));
                List<Apartamento> candidatos = apartamentos.listarVigentesParaVenta().stream()
                        .filter(filtro)
                        .filter(a -> nochesLibres(a, estancia))
                        .sorted(Comparator.comparing(a -> a.getIdentificacion().codigo()))
                        .toList();
                for (Apartamento candidato : candidatos) {
                    try {
                        crear.accept(candidato.getIdentificacion().codigo(), estancia);
                        return;
                    } catch (ReglaDominioException e) {
                        log.debug("Datos de prueba: {} no sirve para {}: {}",
                                candidato.getIdentificacion().codigo(), estancia, e.getMessage());
                    }
                }
            }
            log.warn("Datos de prueba: no se encontraron noches libres para completar una reserva desde {}", entrada);
        }

        /** Ninguna reserva que no esté cancelada usa esas noches del apartamento y no hay bloqueos vigentes. */
        private boolean nochesLibres(Apartamento apartamento, Estancia estancia) {
            boolean ocupadas = reservas.listarTodas().stream()
                    .filter(r -> r.esDelApartamento(apartamento.getIdentificacion()))
                    .filter(r -> r.getEstado() != EstadoReserva.CANCELADA)
                    .anyMatch(r -> r.getEstancia().seSolapaCon(estancia));
            return !ocupadas && !apartamento.estaBloqueadoDurante(estancia);
        }

        /**
         * FINALIZADA: llegó, pagó todo el folio, recibió de vuelta el depósito (RP-02) y salió; el folio queda
         * cerrado en cero (RN-17).
         */
        private void finalizada(String apartamento, Estancia estancia) {
            ReservaConFolio finalizada = confirmadaEnElPasado(apartamento, estancia.fechaEntrada(),
                    estancia.fechaSalida(),
                    titular("Carlos Restrepo", "CC", "1094111222", "carlos.restrepo@correo.co", "3105550101"),
                    CanalOrigen.DIRECTO);
            Reserva reserva = finalizada.reserva();
            Folio folio = finalizada.folio();
            // El apartamento estaba PREPARADO al llegar (RN-11); su estado de hoy ya no depende de esta estancia.
            reserva.registrarLlegada(reserva.getEstancia().fechaEntrada(), EstadoOperativo.PREPARADO);
            LocalDate salida = reserva.getEstancia().fechaSalida();
            folio.registrarPago(new Pago(folio.saldo(), MedioPago.TARJETA_DEBITO_CREDITO, salida));
            folio.devolverDeposito(MedioPago.TARJETA_DEBITO_CREDITO, salida);
            reserva.registrarSalida(salida);
            folio.cerrar(salida);
            guardar(reserva, folio);
        }

        /** NO_SHOW: confirmada, pero el grupo no llegó antes de la hora límite (L-15); retiene el 50 % (RN-13). */
        private void noShow(String apartamento, Estancia estancia) {
            ReservaConFolio noShow = confirmadaEnElPasado(apartamento, estancia.fechaEntrada(), estancia.fechaSalida(),
                    titular(VALENTINA_NOMBRE, "CC", VALENTINA_DOCUMENTO, "huesped2@sga.local", "3115550202"),
                    CanalOrigen.PORTAL);
            Reserva reserva = noShow.reserva();
            Folio folio = noShow.folio();
            LocalDateTime limite = reserva.getEstancia().fechaEntrada().atTime(coral.getHoraLimiteNoShow());
            Dinero retencion = reserva.calcularRetencionPorNoShow();
            reserva.declararNoShow(limite, coral.getHoraLimiteNoShow());
            folio.anularCargos("No-show de la reserva " + reserva.getCodigo().valor(), limite.toLocalDate());
            folio.registrarPenalidad(retencion, "Retención por no-show: "
                    + reserva.getPoliticaCancelacion().consecuenciaNoShow(), limite.toLocalDate());
            guardar(reserva, folio);
        }

        /** EN_CURSO: llegó ayer y el apartamento pasa de PREPARADO a OCUPADO (RN-11, 7.6). */
        private void enCurso(String apartamento, Estancia estancia) {
            ReservaConFolio enCurso = confirmadaEnElPasado(apartamento, estancia.fechaEntrada(),
                    estancia.fechaSalida(),
                    titular("Andrés Mejía", "CC", "1094555666", "andres.mejia@correo.co", "3125550303"),
                    CanalOrigen.DIRECTO);
            Reserva reserva = enCurso.reserva();
            Apartamento apto = apartamento(reserva.getApartamento());
            reserva.registrarLlegada(reserva.getEstancia().fechaEntrada(), apto.getEstadoOperativo());
            apto.recibirGrupo();
            guardar(reserva, enCurso.folio());
            apartamentos.guardar(apto);
        }

        /**
         * PENDIENTE: por el portal aún no paga el anticipo ni se confirma; la externa llega de una plataforma de
         * terceros con su identificador (RN-19) y sin hora de llegada.
         */
        private void pendiente(String apartamento, Estancia estancia, CanalOrigen canal) {
            switch (canal) {
                case PORTAL -> nueva(apartamento, estancia.fechaEntrada(), estancia.fechaSalida(),
                        titular(LAURA_NOMBRE, "CC", LAURA_DOCUMENTO, "huesped1@sga.local", "3135550404"),
                        CanalOrigen.PORTAL, HORA_LLEGADA, null);
                case EXTERNO -> nueva(apartamento, estancia.fechaEntrada(), estancia.fechaSalida(),
                        titular("Emily Johnson", "PA", "55501230", "emily.johnson@mail.com", "3165550707"),
                        CanalOrigen.EXTERNO, null, identificadorExternoLibre());
                case DIRECTO -> nueva(apartamento, estancia.fechaEntrada(), estancia.fechaSalida(),
                        titular("Santiago Herrera", "CC", "1094999000", "santiago.herrera@correo.co", "3145550505"),
                        CanalOrigen.DIRECTO, HORA_LLEGADA, null);
            }
        }

        /** CONFIRMADA en recepción: paga el anticipo del 50 % (L-11) y se confirma (RN-09, RN-21). */
        private void confirmada(String apartamento, Estancia estancia) {
            Reserva confirmada = nueva(apartamento, estancia.fechaEntrada(), estancia.fechaSalida(),
                    titular("Santiago Herrera", "CC", "1094999000", "santiago.herrera@correo.co", "3145550505"),
                    CanalOrigen.DIRECTO, HORA_LLEGADA, null);
            Folio folioConfirmada = folios.buscarPorReserva(confirmada.getCodigo()).orElseThrow();
            folioConfirmada.registrarPago(new Pago(anticipoDe(confirmada), MedioPago.TRANSFERENCIA_BANCARIA,
                    ahora.toLocalDate()));
            folios.guardar(folioConfirmada);
            confirmacion.confirmar(confirmada.getCodigo(), ahora);
        }

        /** CANCELADA con más de 15 días de antelación: retención 0 % (RN-13) y noches liberadas (RN-12). */
        private void cancelada(String apartamento, Estancia estancia) {
            Reserva cancelada = nueva(apartamento, estancia.fechaEntrada(), estancia.fechaSalida(),
                    titular("Mariana Castaño", "CC", "1094121314", "mariana.castano@correo.co", "3155550606"),
                    CanalOrigen.PORTAL, HORA_LLEGADA, null);
            cancelacion.cancelar(cancelada.getCodigo(), ahora.toLocalDate(), "El titular cambió sus planes de viaje");
        }

        /** RN-19: el identificador de la reserva externa de prueba no se repite en su canal. */
        private String identificadorExternoLibre() {
            int numero = 123;
            String identificador = "BK-DEMO-000123";
            while (reservas.buscarPorCanalEIdentificadorExterno(DatosInicialesCanalesConfig.CANAL_DEMO, identificador)
                    .isPresent()) {
                numero++;
                identificador = String.format("BK-DEMO-%06d", numero);
            }
            return identificador;
        }

        private Reserva nueva(String apartamento, LocalDate entrada, LocalDate salida, Titular titular,
                              CanalOrigen canal, LocalTime horaLlegada, String identificadorExterno) {
            // RN-19: la reserva externa guarda el canal que la envió, el mismo de DatosInicialesCanalesConfig.
            String nombreCanal = canal == CanalOrigen.EXTERNO ? DatosInicialesCanalesConfig.CANAL_DEMO : null;
            return creacion.registrarNuevaReserva(new IdentificacionApartamento(apartamento),
                    new Estancia(entrada, salida), titular, List.of(ocupante(titular)), horaLlegada, null,
                    politicaVigente, canal, nombreCanal, identificadorExterno, Mascotas.ninguna(), ahora);
        }

        /**
         * Reserva CONFIRMADA de una estancia que ya empezó, con la cotización y la política vigentes congeladas
         * (RN-22) y su folio abierto como en CU-03 (cargo de alojamiento y depósito, RP-02) con el anticipo pagado
         * (L-11). Se creó 15 días antes de la entrada y se confirmó dos horas después.
         */
        private ReservaConFolio confirmadaEnElPasado(String apartamento, LocalDate entrada, LocalDate salida,
                                                     Titular titular, CanalOrigen canal) {
            Apartamento apto = apartamento(new IdentificacionApartamento(apartamento));
            Estancia estancia = new Estancia(entrada, salida);
            List<Ocupante> ocupantes = List.of(ocupante(titular));
            Cotizacion cotizacion = cotizador.cotizar(apto, estancia, ocupantes, coral.getUmbralEdadFacturable());
            LocalDateTime creacionReserva = entrada.minusDays(15).atTime(10, 0);
            CodigoReserva codigo = siguienteCodigo(creacionReserva.getYear());

            Reserva reserva = Reserva.reconstruir(codigo, apto.getIdentificacion(), estancia, titular, ocupantes,
                    canal, null, HORA_LLEGADA, cotizacion, politicaVigente, EstadoReserva.CONFIRMADA,
                    creacionReserva, creacionReserva.plusHours(2), null, null, null, null);

            LocalDate apertura = creacionReserva.toLocalDate();
            Cargo alojamiento = new Cargo(TipoCargo.ALOJAMIENTO,
                    "Alojamiento " + estancia.noches() + " noches en " + apartamento, cotizacion.valorTotal(), apertura);
            Folio folio = Folio.abrir("FOL-" + codigo.valor(), codigo, alojamiento, depositoGarantia, apertura);
            folio.registrarPago(new Pago(anticipoDe(reserva), MedioPago.TRANSFERENCIA_BANCARIA, apertura));
            return new ReservaConFolio(reserva, folio);
        }

        private Dinero anticipoDe(Reserva reserva) {
            return reserva.valorTotal().porcentaje(coral.getPorcentajeAnticipo());
        }

        /** Mismo formato y secuencia que CreacionReservaService: RES-YYYY-NNNNN. */
        private CodigoReserva siguienteCodigo(int anio) {
            int ultima = reservas.listarTodas().stream()
                    .map(Reserva::getCodigo)
                    .filter(codigo -> codigo.anio() == anio)
                    .mapToInt(CodigoReserva::secuencia)
                    .max()
                    .orElse(0);
            return CodigoReserva.generar(anio, ultima + 1);
        }

        private Apartamento apartamento(IdentificacionApartamento identificacion) {
            return apartamentos.buscarPorIdentificacion(identificacion)
                    .orElseThrow(() -> new ReglaDominioException("No existe el apartamento " + identificacion.codigo()));
        }

        private void guardar(Reserva reserva, Folio folio) {
            reservas.guardar(reserva);
            folios.guardar(folio);
        }
    }

    private record ReservaConFolio(Reserva reserva, Folio folio) {
    }

    private static Titular titular(String nombre, String tipoDocumento, String documento, String email,
                                   String telefono) {
        return new Titular(nombre, new DocumentoIdentidad(tipoDocumento, documento), email, telefono);
    }

    /** 3.2: el titular viaja y es un ocupante facturable (adulto). */
    private static Ocupante ocupante(Titular titular) {
        return new Ocupante(titular.nombre(), titular.documento(),
                NACIMIENTO_POR_DOCUMENTO.getOrDefault(titular.documento().numero(), NACIMIENTO_POR_DEFECTO));
    }
}

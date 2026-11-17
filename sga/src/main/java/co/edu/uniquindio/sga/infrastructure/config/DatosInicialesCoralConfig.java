package co.edu.uniquindio.sga.infrastructure.config;

import co.edu.uniquindio.sga.domain.entity.Alojamiento;
import co.edu.uniquindio.sga.domain.entity.Apartamento;
import co.edu.uniquindio.sga.domain.entity.ServicioAdicional;
import co.edu.uniquindio.sga.domain.entity.Temporada;
import co.edu.uniquindio.sga.domain.exception.ReglaDominioException;
import co.edu.uniquindio.sga.domain.repository.AlojamientoRepository;
import co.edu.uniquindio.sga.domain.repository.ApartamentoRepository;
import co.edu.uniquindio.sga.domain.repository.BloqueoRepository;
import co.edu.uniquindio.sga.domain.repository.ReservaRepository;
import co.edu.uniquindio.sga.domain.service.RegistrarBloqueoService;
import co.edu.uniquindio.sga.domain.valueobject.Capacidad;
import co.edu.uniquindio.sga.domain.valueobject.Caracteristicas;
import co.edu.uniquindio.sga.domain.valueobject.Coordenada;
import co.edu.uniquindio.sga.domain.valueobject.Dinero;
import co.edu.uniquindio.sga.domain.valueobject.Dotacion;
import co.edu.uniquindio.sga.domain.valueobject.EstadoOperativo;
import co.edu.uniquindio.sga.domain.valueobject.Estancia;
import co.edu.uniquindio.sga.domain.valueobject.IdentificacionApartamento;
import co.edu.uniquindio.sga.domain.valueobject.Imagen;
import co.edu.uniquindio.sga.domain.valueobject.RangoFechas;
import co.edu.uniquindio.sga.domain.valueobject.Tarifa;
import co.edu.uniquindio.sga.domain.valueobject.TipoTemporada;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;

/**
 * Datos iniciales del alojamiento Coral (Anexo A). H2 en memoria arranca vacía en cada ejecución y
 * sin alojamiento, temporadas, tarifas y servicios ningún caso de uso funciona (F-13).
 * La carga usa las fábricas del dominio, así que pasa por las mismas reglas (F-05, 7.4, L-03, L-04).
 * Los parámetros de A.4 (con RP-02 y la edad mínima de la cuenta) los carga aparte DatosInicialesParametrosConfig,
 * que revisa los suyos aunque el alojamiento ya exista. El valor inicial del recargo de RP-03 sale de
 * application.properties. El catálogo (identidad, temporadas, tarifas, inventario y demás servicios) es dato inicial.
 * Las temporadas se registran con la fecha final excluida (nota de A.3).
 *
 * Datos de prueba (12.3): APT-202 queda FUERA_DE_SERVICIO con un bloqueo de hoy a hoy+15 (3.3: el estado no
 * retira la venta, el bloqueo sí), APT-101 tiene un bloqueo de hoy+4 a hoy+7 y APT-301 uno de hoy+20 a hoy+25.
 * Ninguno se cruza con las reservas de prueba. Si el alojamiento ya existe no se duplica nada: solo se completa lo
 * mínimo que falte (ver Completador). Corre con @Order(1), antes que cualquier otro cargador de datos.
 */
@Configuration
public class DatosInicialesCoralConfig {

    private static final Logger log = LoggerFactory.getLogger(DatosInicialesCoralConfig.class);

    private static final String BASE = "T-BASE";
    private static final String MEDIA = "T-MEDIA";
    private static final String ALTA = "T-ALTA";

    /** Prefijo común de las imágenes: formato y calidad automáticos y 1600 px de ancho. */
    private static final String CLOUDINARY = "https://res.cloudinary.com/xwkp6b4t/image/upload/f_auto,q_auto,w_1600/";

    /** RP-03: la hora desde la que una llegada es nocturna es un parámetro editable; la descripción no la repite */
    static final String DESCRIPCION_RECEPCION_NOCTURNA =
            "Recepción para las llegadas nocturnas, desde la hora configurada en el alojamiento (RP-03)";

    @Bean
    @Order(1)
    public CommandLineRunner cargarDatosCoral(
            AlojamientoRepository alojamientoRepository,
            ApartamentoRepository apartamentoRepository,
            BloqueoRepository bloqueoRepository,
            ReservaRepository reservaRepository,
            RegistrarBloqueoService registrarBloqueoService,
            Clock reloj,
            @Value("${sga.alojamiento.hora-entrada}") LocalTime horaEntrada,
            @Value("${sga.alojamiento.hora-salida}") LocalTime horaSalida,
            @Value("${sga.alojamiento.id-servicio-recepcion-nocturna}") String idRecepcionNocturna,
            @Value("${sga.alojamiento.valor-recepcion-nocturna}") long valorRecepcionNocturna) {
        return args -> {
            Optional<Alojamiento> existente = alojamientoRepository.obtener();
            if (existente.isPresent()) {
                // Base ya cargada: no se duplica nada, solo se completa lo mínimo que le falte
                new Completador(existente.get(), alojamientoRepository, apartamentoRepository, bloqueoRepository,
                        reservaRepository, registrarBloqueoService, LocalDate.now(reloj)).completar();
                return;
            }

            // A.1 Identidad. La coordenada es la de Bocagrande (aproximada: el Anexo A no la trae).
            Alojamiento coral = Alojamiento.crear("ALO-CORAL", "Coral",
                    "Conjunto residencial exclusivo de apartamentos independientes de alta gama, con balcones "
                            + "panorámicos frente al mar, hidromasaje privado y acabados en piedra coralina.",
                    "Cartagena de Indias",
                    "Sector Bocagrande, Avenida del Malecón, frente a las playas de Castillo Grande",
                    new Coordenada(10.3975, -75.5590), horaEntrada, horaSalida,
                    "Silencio estricto en balcones entre las 23:00 y las 07:00; prohibido fumar dentro de los "
                            + "apartamentos; uso respetuoso de las áreas comunes no vendibles.");

            // A.4 Los parámetros y políticas los carga DatosInicialesParametrosConfig, que revisa los suyos aparte

            // A.3 Temporadas
            coral.agregarTemporada(Temporada.crearBase(BASE, "Base"));
            coral.agregarTemporada(Temporada.crear(MEDIA, "Media", TipoTemporada.MEDIA, List.of(
                    rango(2026, 1, 21, 2026, 2, 1),          // regreso de vacaciones
                    rango(2026, 3, 20, 2026, 3, 24),         // puente de San José
                    rango(2026, 6, 1, 2026, 6, 15),          // antesala de vacaciones
                    rango(2026, 7, 21, 2026, 8, 1),          // cierre de vacaciones
                    rango(2026, 10, 30, 2026, 11, 3),        // puente de Todos los Santos
                    rango(2026, 11, 6, 2026, 11, 17),        // Fiestas de Independencia de Cartagena
                    rango(2026, 12, 1, 2026, 12, 10))));     // antesala de fin de año
            coral.agregarTemporada(Temporada.crear(ALTA, "Alta", TipoTemporada.ALTA, List.of(
                    rango(2026, 1, 1, 2026, 1, 21),          // cierre de la temporada de fin de año 2025
                    rango(2026, 3, 27, 2026, 4, 6),          // Semana Santa
                    rango(2026, 6, 15, 2026, 7, 21),         // vacaciones de mitad de año
                    rango(2026, 10, 5, 2026, 10, 13),        // receso escolar
                    rango(2026, 12, 10, 2027, 1, 21))));     // fin de año

            // A.5 Servicios adicionales (L-17). RP-03: la recepción nocturna genera el cargo configurado.
            coral.agregarServicioAdicional(ServicioAdicional.crearConCargo(idRecepcionNocturna, "Recepción nocturna",
                    DESCRIPCION_RECEPCION_NOCTURNA, Dinero.de(valorRecepcionNocturna)));
            coral.agregarServicioAdicional(ServicioAdicional.crearConCargo("SRV-PASEO-BAHIA",
                    "Paseo privado por la bahía", "Recorrido privado por la bahía de Cartagena", Dinero.de(420_000)));
            coral.agregarServicioAdicional(ServicioAdicional.crearConCargo("SRV-CENA-BALCON",
                    "Cena en balcón por cocinero privado", "Cena para 2 personas en el balcón", Dinero.de(260_000)));

            // A.2 Inventario y tarifas de A.3 (Base, Media, Alta por ocupante facturable)
            List<Apartamento> inventario = INVENTARIO.stream().map(DatosInicialesCoralConfig::apartamentoInicial).toList();

            for (Apartamento apartamento : inventario) {
                apartamento.activar(coral.getTemporadas());          // 7.4: exige tarifa en todas las temporadas
                coral.agregarApartamento(apartamento);
                apartamentoRepository.guardar(apartamento);
            }
            alojamientoRepository.guardar(coral);

            // 12.3: APT-202 fuera de servicio por decisión administrativa (7.6) y bloqueado desde hoy (3.3: el estado
            // no retira la venta, el bloqueo sí)
            LocalDate hoy = LocalDate.now(reloj);
            IdentificacionApartamento apt202 = new IdentificacionApartamento(APARTAMENTO_FUERA_DE_SERVICIO);
            Apartamento fueraDeServicio = apartamentoRepository.buscarPorIdentificacion(apt202).orElseThrow();
            fueraDeServicio.ponerFueraDeServicio();
            apartamentoRepository.guardar(fueraDeServicio);
            registrarBloqueoService.registrarBloqueo(apt202, new RangoFechas(hoy, hoy.plusDays(15)),
                    MOTIVO_FUERA_DE_SERVICIO);

            // 12.3: bloqueos vigentes sobre APT-101 y APT-301 (RN-07), sin reservas de prueba en esas noches
            registrarBloqueoService.registrarBloqueo(new IdentificacionApartamento("APT-101"),
                    new RangoFechas(hoy.plusDays(4), hoy.plusDays(7)), "Mantenimiento del aire acondicionado");
            registrarBloqueoService.registrarBloqueo(new IdentificacionApartamento("APT-301"),
                    new RangoFechas(hoy.plusDays(20), hoy.plusDays(25)), "Mantenimiento de la piscina de inmersión");

            log.info("Datos iniciales del alojamiento Coral cargados: {} apartamentos", inventario.size());
        };
    }

    /**
     * Completa una base que ya tiene el alojamiento, sin duplicar lo que existe: los apartamentos del inventario
     * que falten (con sus tarifas y activos), las tarifas que les falten en las temporadas iniciales, un apartamento
     * FUERA_DE_SERVICIO con su bloqueo si no hay ninguno y un bloqueo vigente si no hay ninguno. Los bloqueos se
     * registran con RegistrarBloqueoService, así que nunca caen sobre noches de reservas activas (RN-01).
     */
    private record Completador(Alojamiento coral, AlojamientoRepository alojamientos,
                               ApartamentoRepository apartamentos, BloqueoRepository bloqueos,
                               ReservaRepository reservas, RegistrarBloqueoService registrarBloqueo, LocalDate hoy) {

        void completar() {
            completarInventario();
            completarFueraDeServicio();
            completarBloqueoVigente();
        }

        private void completarInventario() {
            boolean alojamientoCambio = false;
            for (ApartamentoInicial datos : INVENTARIO) {
                IdentificacionApartamento id = new IdentificacionApartamento(datos.id());
                Optional<Apartamento> guardado = apartamentos.buscarPorIdentificacion(id);
                if (guardado.isPresent()) {
                    completarTarifas(guardado.get(), datos);
                    continue;
                }
                try {
                    Apartamento nuevo = apartamentoInicial(datos);
                    nuevo.activar(coral.getTemporadas());          // 7.4: exige tarifa en todas las temporadas
                    coral.agregarApartamento(nuevo);               // L-03 y L-04
                    apartamentos.guardar(nuevo);
                    alojamientoCambio = true;
                    log.info("Datos de prueba: se agregó el apartamento {}", datos.id());
                } catch (ReglaDominioException e) {
                    log.warn("Datos de prueba: no se pudo agregar {}: {}", datos.id(), e.getMessage());
                }
            }
            if (alojamientoCambio) {
                alojamientos.guardar(coral);
            }
        }

        /** 7.4: define desde hoy las tarifas iniciales que le falten al apartamento en las temporadas iniciales. */
        private void completarTarifas(Apartamento apartamento, ApartamentoInicial datos) {
            if (apartamento.isEliminado() || apartamento.tieneTarifasCompletas(coral.getTemporadas())) {
                return;
            }
            for (int i = 0; i < TEMPORADAS.size(); i++) {
                Optional<Temporada> temporada = coral.buscarTemporada(TEMPORADAS.get(i));
                if (temporada.isPresent() && !apartamento.tieneTarifasCompletas(List.of(temporada.get()))) {
                    apartamento.definirTarifa(new Tarifa(TEMPORADAS.get(i), Dinero.de(datos.tarifas().get(i))), hoy);
                }
            }
            apartamentos.guardar(apartamento);
            log.info("Datos de prueba: se completaron las tarifas de {}", datos.id());
        }

        /**
         * 12.3: si ningún apartamento está FUERA_DE_SERVICIO, pone uno (prefiere APT-202) con un bloqueo de hoy a
         * hoy+15 (3.3: el estado no retira la venta, el bloqueo sí). Solo sirve uno que no esté OCUPADO ni tenga
         * reservas activas en esas noches.
         */
        private void completarFueraDeServicio() {
            List<Apartamento> vigentes = apartamentos.listarNoEliminados();
            if (vigentes.stream().anyMatch(a -> a.getEstadoOperativo() == EstadoOperativo.FUERA_DE_SERVICIO)) {
                return;
            }
            RangoFechas rango = new RangoFechas(hoy, hoy.plusDays(15));
            Estancia noches = new Estancia(rango.fechaInicio(), rango.fechaFin());
            Optional<Apartamento> candidato = vigentes.stream()
                    .sorted(Comparator.comparing(
                            (Apartamento a) -> !a.getIdentificacion().codigo().equals(APARTAMENTO_FUERA_DE_SERVICIO)))
                    .filter(a -> a.getEstadoOperativo() != EstadoOperativo.OCUPADO)
                    .filter(a -> reservas.listarActivasQueSeSolapan(a.getIdentificacion(), noches).isEmpty())
                    .findFirst();
            if (candidato.isEmpty()) {
                log.warn("Datos de prueba: ningún apartamento puede quedar fuera de servicio sin chocar con reservas");
                return;
            }
            IdentificacionApartamento id = candidato.get().getIdentificacion();
            if (!candidato.get().estaBloqueadoEn(hoy)) {
                registrarBloqueo.registrarBloqueo(id, rango, MOTIVO_FUERA_DE_SERVICIO);
            }
            Apartamento fueraDeServicio = apartamentos.buscarPorIdentificacion(id).orElseThrow();
            fueraDeServicio.ponerFueraDeServicio();
            apartamentos.guardar(fueraDeServicio);
            log.info("Datos de prueba: {} quedó fuera de servicio", id.codigo());
        }

        /** 12.3: si no hay ningún bloqueo vigente que no haya terminado, registra uno en noches sin reservas. */
        private void completarBloqueoVigente() {
            List<Apartamento> vigentes = apartamentos.listarNoEliminados();
            boolean hayBloqueo = vigentes.stream()
                    .flatMap(a -> bloqueos.listarVigentesPorApartamento(a.getIdentificacion()).stream())
                    .anyMatch(b -> b.getRango().fechaFin().isAfter(hoy));
            if (hayBloqueo) {
                return;
            }
            for (int inicio = 20; inicio <= 120; inicio += 7) {
                RangoFechas rango = new RangoFechas(hoy.plusDays(inicio), hoy.plusDays(inicio + 5));
                Estancia noches = new Estancia(rango.fechaInicio(), rango.fechaFin());
                Optional<Apartamento> libre = vigentes.stream()
                        .filter(a -> reservas.listarActivasQueSeSolapan(a.getIdentificacion(), noches).isEmpty())
                        .findFirst();
                if (libre.isPresent()) {
                    registrarBloqueo.registrarBloqueo(libre.get().getIdentificacion(), rango,
                            "Mantenimiento preventivo");
                    log.info("Datos de prueba: bloqueo vigente en {}", libre.get().getIdentificacion().codigo());
                    return;
                }
            }
        }
    }

    /** 12.3: apartamento de los datos iniciales que queda fuera de servicio, con su bloqueo desde hoy */
    static final String APARTAMENTO_FUERA_DE_SERVICIO = "APT-202";
    static final String MOTIVO_FUERA_DE_SERVICIO = "Fuera de servicio: reparación de la cocina integral";

    /** Temporadas de los datos iniciales, en el orden de las tarifas de {@link #INVENTARIO} */
    static final List<String> TEMPORADAS = List.of(BASE, MEDIA, ALTA);

    /** A.2: un apartamento de los datos iniciales, con sus tarifas Base, Media y Alta por ocupante facturable */
    record ApartamentoInicial(String id, String nombre, String descripcion, int dormitorios, int capacidad,
                              List<Long> tarifas) {
    }

    /** A.2 Inventario y tarifas de A.3; también sirven para completar una base que ya existe */
    static final List<ApartamentoInicial> INVENTARIO = List.of(
            new ApartamentoInicial("APT-101", "Coral Luminoso", "Pensado para parejas o viajes de descanso individual.",
                    1, 2, List.of(310_000L, 430_000L, 620_000L)),
            new ApartamentoInicial("APT-102", "Arrecife Rosado", "Orientado a parejas jóvenes o profesionales.",
                    1, 2, List.of(330_000L, 460_000L, 660_000L)),
            new ApartamentoInicial("APT-201", "Marea Dorada", "Diseñado para familias o pequeños grupos.",
                    2, 4, List.of(270_000L, 380_000L, 560_000L)),
            new ApartamentoInicial("APT-202", "Bahía Cristal", "Distribución funcional para grupos familiares.",
                    2, 4, List.of(280_000L, 390_000L, 580_000L)),
            new ApartamentoInicial("APT-301", "Gran Coral Imperial", "La unidad insignia de máxima capacidad.",
                    3, 6, List.of(250_000L, 350_000L, 520_000L)));

    /** 7.3: imágenes de cada apartamento en el servicio externo (Cloudinary); la primera es la principal. */
    private static final Map<String, List<String>> IMAGENES = Map.of(
            "APT-101", List.of(
                    CLOUDINARY + "apt-101-1.jpg",
                    CLOUDINARY + "apt-101-2.jpg",
                    CLOUDINARY + "apt-101-3.jpg"),
            "APT-102", List.of(
                    CLOUDINARY + "apt-102-1.jpg",
                    CLOUDINARY + "apt-102-2.jpg",
                    CLOUDINARY + "apt-102-3.jpg"),
            "APT-201", List.of(
                    CLOUDINARY + "apt-201-1.jpg",
                    CLOUDINARY + "apt-201-2.jpg",
                    CLOUDINARY + "apt-201-3.jpg",
                    CLOUDINARY + "apt-201-4.jpg"),
            "APT-202", List.of(
                    CLOUDINARY + "apt-202-1.jpg",
                    CLOUDINARY + "apt-202-2.jpg",
                    CLOUDINARY + "apt-202-3.jpg"),
            "APT-301", List.of(
                    CLOUDINARY + "apt-301-1.jpg",
                    CLOUDINARY + "apt-301-2.jpg",
                    CLOUDINARY + "apt-301-3.jpg"));

    /** A.2: lo que trae cada apartamento (dotación), sin los rasgos del espacio */
    static final Map<String, List<String>> DOTACION = Map.of(
            "APT-101", List.of("Cama extragrande con lencería de algodón egipcio",
                    "Baño privado en mármol con doble ducha", "Cocina integral abierta", "Tina de hidromasaje privada"),
            "APT-102", List.of("Cama extragrande", "Baño de lujo", "Cocina equipada con barra de cuarzo",
                    "Cava refrigerada de vinos", "Cama balinesa"),
            "APT-201", List.of("Dormitorio principal con cama extragrande",
                    "Dormitorio auxiliar con dos camas semidobles", "2 baños completos", "Salón comedor",
                    "Cocina completa", "Hidromasaje exterior"),
            "APT-202", List.of("Dormitorio principal extragrande", "Dormitorio con dos camas individuales",
                    "2 baños privados", "Cocina integral", "Comedor", "Hamaca artesanal de San Jacinto"),
            "APT-301", List.of("Dormitorio principal con vestier y baño privado", "2 dormitorios dobles",
                    "3 baños completos", "Sala", "Cocina amplia", "Piscina de inmersión"));

    /**
     * 7.3: rasgos de cada apartamento (características), aparte de su dotación. Las mascotas no son un rasgo del
     * apartamento: las admite o no la política del alojamiento (L-16), igual para todos sus apartamentos.
     */
    static final Map<String, List<String>> CARACTERISTICAS = Map.of(
            "APT-101", List.of("Vista al mar", "Frente a la playa", "Balcón"),
            "APT-102", List.of("Vista a la bahía", "Terraza"),
            "APT-201", List.of("Terraza"),
            "APT-202", List.of("Balcón"),
            "APT-301", List.of("Vista al mar", "Vista a las murallas", "Terraza", "Piso alto"));

    /** Apartamento de los datos iniciales, inactivo, con su dotación, características, imágenes y tarifas */
    static Apartamento apartamentoInicial(ApartamentoInicial datos) {
        String id = datos.id();
        Apartamento apartamento = Apartamento.crear(new IdentificacionApartamento(id), datos.nombre(),
                datos.descripcion(), datos.dormitorios(), new Capacidad(datos.capacidad()),
                new Dotacion(DOTACION.get(id)), imagenes(id));
        apartamento.definirCaracteristicas(new Caracteristicas(CARACTERISTICAS.get(id)));
        for (int i = 0; i < TEMPORADAS.size(); i++) {
            apartamento.definirTarifa(new Tarifa(TEMPORADAS.get(i), Dinero.de(datos.tarifas().get(i))));
        }
        return apartamento;
    }

    /** Todas las imágenes del apartamento en su orden; solo la primera queda como principal (7.3). */
    private static List<Imagen> imagenes(String id) {
        List<String> urls = IMAGENES.get(id);
        return IntStream.range(0, urls.size())
                .mapToObj(i -> new Imagen(urls.get(i), i == 0))
                .toList();
    }

    private static RangoFechas rango(int anioInicio, int mesInicio, int diaInicio, int anioFin, int mesFin, int diaFin) {
        return new RangoFechas(LocalDate.of(anioInicio, mesInicio, diaInicio), LocalDate.of(anioFin, mesFin, diaFin));
    }
}
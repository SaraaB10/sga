package co.edu.uniquindio.sga.infrastructure.config;

import co.edu.uniquindio.sga.domain.entity.Alojamiento;
import co.edu.uniquindio.sga.domain.entity.ServicioAdicional;
import co.edu.uniquindio.sga.domain.repository.AlojamientoRepository;
import co.edu.uniquindio.sga.domain.valueobject.CargoAseoMascota;
import co.edu.uniquindio.sga.domain.valueobject.DepositoGarantia;
import co.edu.uniquindio.sga.domain.valueobject.Dinero;
import co.edu.uniquindio.sga.domain.valueobject.EstanciaMinima;
import co.edu.uniquindio.sga.domain.valueobject.PlazoConfirmacion;
import co.edu.uniquindio.sga.domain.valueobject.TiempoPreparacion;
import co.edu.uniquindio.sga.domain.valueobject.UmbralEdadFacturable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

import java.time.LocalTime;

/**
 * Valores iniciales de los parámetros del alojamiento (A.4, RP-02, RP-03 y edad mínima de la cuenta). Desde aquí
 * viven en la base y los edita el administrador (PUT /api/alojamiento/parametros): application.properties solo da el
 * valor con el que nacen. Este cargador revisa sus propios datos, no la existencia del alojamiento, para que una base
 * creada antes de alguno de estos parámetros los reciba al arrancar:
 * <ul>
 *   <li>Los de la sección 6 que estén vacíos toman el valor de application.properties; los que ya tienen valor se
 *       conservan (pudo cambiarlos el administrador).</li>
 *   <li>La política de mascotas se define solo si el alojamiento no tenía ningún parámetro: con la sección 6 ya
 *       configurada, "no acepta mascotas" es una decisión guardada, no un vacío.</li>
 *   <li>El depósito de garantía y la edad mínima de la cuenta, si faltan.</li>
 *   <li>El servicio de recepción nocturna (RP-03), si no existe.</li>
 * </ul>
 * Corre con @Order(1) e invoca antes el cargador del alojamiento (idempotente), que es quien crea el alojamiento.
 */
@Configuration
public class DatosInicialesParametrosConfig {

    private static final Logger log = LoggerFactory.getLogger(DatosInicialesParametrosConfig.class);

    @Bean
    @Order(1)
    public CommandLineRunner cargarParametrosAlojamiento(
            @Qualifier("cargarDatosCoral") CommandLineRunner cargarDatosCoral,
            AlojamientoRepository alojamientoRepository,
            @Value("${sga.alojamiento.umbral-edad-facturable}") int umbralEdad,
            @Value("${sga.alojamiento.tiempo-preparacion-horas}") int horasPreparacion,
            @Value("${sga.alojamiento.plazo-confirmacion-horas}") int horasConfirmacion,
            @Value("${sga.alojamiento.hora-limite-no-show}") LocalTime horaLimiteNoShow,
            @Value("${sga.alojamiento.hora-inicio-llegada-nocturna}") LocalTime horaLlegadaNocturna,
            @Value("${sga.alojamiento.estancia-minima-noches}") int nochesEstanciaMinima,
            @Value("${sga.alojamiento.estancia-minima-activa}") boolean estanciaMinimaActiva,
            @Value("${sga.alojamiento.exige-anticipo}") boolean exigeAnticipo,
            @Value("${sga.alojamiento.porcentaje-anticipo}") int porcentajeAnticipo,
            @Value("${sga.alojamiento.mascotas.aceptadas}") boolean aceptaMascotas,
            @Value("${sga.alojamiento.mascotas.cargo-aseo}") long cargoAseoMascota,
            @Value("${sga.alojamiento.mascotas.maximo}") int maximoMascotas,
            @Value("${sga.alojamiento.mascotas.peso-maximo-kg}") int pesoMaximoMascotaKg,
            @Value("${sga.alojamiento.deposito-garantia-concepto}") String conceptoDeposito,
            @Value("${sga.alojamiento.deposito-garantia}") long deposito,
            @Value("${sga.usuarios.edad-minima}") int edadMinimaCuenta,
            @Value("${sga.alojamiento.id-servicio-recepcion-nocturna}") String idRecepcionNocturna,
            @Value("${sga.alojamiento.valor-recepcion-nocturna}") long valorRecepcionNocturna) {
        return args -> {
            cargarDatosCoral.run(args);
            Alojamiento alojamiento = alojamientoRepository.obtener().orElse(null);
            if (alojamiento == null) {
                return;
            }
            boolean cambio = false;

            boolean sinNinguno = alojamiento.getUmbralEdadFacturable() == null
                    && alojamiento.getTiempoPreparacion() == null && alojamiento.getPlazoConfirmacion() == null
                    && alojamiento.getHoraLimiteNoShow() == null && alojamiento.getHoraInicioLlegadaNocturna() == null
                    && alojamiento.getEstanciaMinima() == null;
            boolean faltaAlguno = alojamiento.getUmbralEdadFacturable() == null
                    || alojamiento.getTiempoPreparacion() == null || alojamiento.getPlazoConfirmacion() == null
                    || alojamiento.getHoraLimiteNoShow() == null || alojamiento.getHoraInicioLlegadaNocturna() == null
                    || alojamiento.getEstanciaMinima() == null;
            if (faltaAlguno) {
                alojamiento.configurarParametros(
                        valor(alojamiento.getUmbralEdadFacturable(), new UmbralEdadFacturable(umbralEdad)),
                        valor(alojamiento.getTiempoPreparacion(), new TiempoPreparacion(horasPreparacion)),
                        valor(alojamiento.getPlazoConfirmacion(), new PlazoConfirmacion(horasConfirmacion)),
                        valor(alojamiento.getHoraLimiteNoShow(), horaLimiteNoShow),
                        valor(alojamiento.getHoraInicioLlegadaNocturna(), horaLlegadaNocturna),
                        valor(alojamiento.getEstanciaMinima(), new EstanciaMinima(nochesEstanciaMinima,
                                estanciaMinimaActiva)),
                        sinNinguno ? exigeAnticipo : alojamiento.isExigeAnticipo(),
                        sinNinguno ? porcentajeAnticipo : alojamiento.getPorcentajeAnticipo());
                cambio = true;
            }
            if (sinNinguno) {
                alojamiento.definirPoliticaMascotas(aceptaMascotas
                        ? new CargoAseoMascota(true, Dinero.de(cargoAseoMascota), maximoMascotas, pesoMaximoMascotaKg)
                        : CargoAseoMascota.noSeAceptanMascotas());
            }
            if (alojamiento.getDepositoGarantia() == null) {
                alojamiento.configurarDepositoGarantia(DepositoGarantia.de(conceptoDeposito, Dinero.de(deposito)));
                cambio = true;
            }
            if (alojamiento.getEdadMinimaCuenta() == null) {
                alojamiento.configurarEdadMinimaCuenta(edadMinimaCuenta);
                cambio = true;
            }
            if (alojamiento.buscarServicioAdicional(idRecepcionNocturna).isEmpty()) {
                alojamiento.agregarServicioAdicional(ServicioAdicional.crearConCargo(idRecepcionNocturna,
                        "Recepción nocturna", DatosInicialesCoralConfig.DESCRIPCION_RECEPCION_NOCTURNA,
                        Dinero.de(valorRecepcionNocturna)));
                cambio = true;
            }
            if (cambio) {
                alojamientoRepository.guardar(alojamiento);
                log.info("Parámetros del alojamiento completados con los valores iniciales de application.properties");
            }
        };
    }

    private static <T> T valor(T actual, T inicial) {
        return actual != null ? actual : inicial;
    }
}

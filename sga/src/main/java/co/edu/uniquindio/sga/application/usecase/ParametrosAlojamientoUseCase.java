package co.edu.uniquindio.sga.application.usecase;

import co.edu.uniquindio.sga.domain.entity.Alojamiento;
import co.edu.uniquindio.sga.domain.exception.ReglaDominioException;
import co.edu.uniquindio.sga.domain.repository.AlojamientoRepository;
import co.edu.uniquindio.sga.domain.service.GestionServiciosAdicionalesService;
import co.edu.uniquindio.sga.domain.valueobject.CargoAseoMascota;
import co.edu.uniquindio.sga.domain.valueobject.DepositoGarantia;
import co.edu.uniquindio.sga.domain.valueobject.Dinero;
import co.edu.uniquindio.sga.domain.valueobject.EstanciaMinima;
import co.edu.uniquindio.sga.domain.valueobject.PlazoConfirmacion;
import co.edu.uniquindio.sga.domain.valueobject.TiempoPreparacion;
import co.edu.uniquindio.sga.domain.valueobject.UmbralEdadFacturable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.Objects;

/**
 * PUT /api/alojamiento/parametros
 * El administrador cambia los parámetros del alojamiento: umbral de edad facturable, tiempo de preparación, plazo de
 * confirmación (también es el plazo para pagar el anticipo), hora límite de no-show, recepción nocturna (hora y
 * recargo), estancia mínima, anticipo (si se exige y su porcentaje), depósito de garantía, mascotas y edad mínima de
 * la cuenta. Se guardan en la base y rigen desde la siguiente operación, sin reiniciar: los servicios del dominio los
 * leen del alojamiento cada vez. Las reglas de cada valor las aplican el agregado y sus objetos de valor (409).
 * Actor: administrador.
 */
@Service
public class ParametrosAlojamientoUseCase {

    /** Todos los parámetros que edita el administrador; el dinero viaja en pesos enteros */
    public record DatosParametros(int umbralEdadFacturable, int tiempoPreparacionHoras, int plazoConfirmacionHoras,
                                  LocalTime horaLimiteNoShow, LocalTime horaInicioLlegadaNocturna,
                                  long recargoRecepcionNocturna, int estanciaMinimaNoches,
                                  boolean estanciaMinimaActiva, boolean exigeAnticipo, int porcentajeAnticipo,
                                  String conceptoDepositoGarantia, long depositoGarantia, boolean aceptaMascotas,
                                  long cargoAseoMascota, int maximoMascotas, int pesoMaximoMascotaKg,
                                  int edadMinimaCuenta) {
    }

    private final AlojamientoRepository alojamientoRepository;
    private final GestionServiciosAdicionalesService serviciosAdicionales;

    public ParametrosAlojamientoUseCase(AlojamientoRepository alojamientoRepository,
                                        GestionServiciosAdicionalesService serviciosAdicionales) {
        this.alojamientoRepository = Objects.requireNonNull(alojamientoRepository);
        this.serviciosAdicionales = Objects.requireNonNull(serviciosAdicionales);
    }

    @Transactional
    public Alojamiento actualizar(DatosParametros datos) {
        if (datos == null) throw new ReglaDominioException("Los parámetros del alojamiento son obligatorios");
        Alojamiento alojamiento = alojamientoRepository.obtener()
                .orElseThrow(() -> new ReglaDominioException("F-13: No hay un alojamiento configurado"));

        alojamiento.configurarParametros(new UmbralEdadFacturable(datos.umbralEdadFacturable()),
                new TiempoPreparacion(datos.tiempoPreparacionHoras()),
                new PlazoConfirmacion(datos.plazoConfirmacionHoras()), datos.horaLimiteNoShow(),
                datos.horaInicioLlegadaNocturna(),
                new EstanciaMinima(datos.estanciaMinimaNoches(), datos.estanciaMinimaActiva()),
                datos.exigeAnticipo(), datos.porcentajeAnticipo());
        alojamiento.definirPoliticaMascotas(datos.aceptaMascotas()
                ? new CargoAseoMascota(true, Dinero.de(datos.cargoAseoMascota()), datos.maximoMascotas(),
                datos.pesoMaximoMascotaKg())
                : CargoAseoMascota.noSeAceptanMascotas());
        alojamiento.configurarDepositoGarantia(
                DepositoGarantia.de(datos.conceptoDepositoGarantia(), Dinero.de(datos.depositoGarantia())));
        alojamiento.configurarEdadMinimaCuenta(datos.edadMinimaCuenta());
        serviciosAdicionales.definirRecargoRecepcionNocturna(alojamiento, Dinero.de(datos.recargoRecepcionNocturna()));
        return alojamientoRepository.guardar(alojamiento);
    }
}

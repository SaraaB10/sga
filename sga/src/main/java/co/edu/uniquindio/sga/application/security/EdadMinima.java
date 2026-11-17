package co.edu.uniquindio.sga.application.security;

import co.edu.uniquindio.sga.domain.exception.ReglaDominioException;
import co.edu.uniquindio.sga.domain.repository.AlojamientoRepository;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.Period;
import java.util.Objects;

/**
 * Edad mínima para crear una cuenta y para ser titular de una reserva tomada por la recepción: el titular es el
 * responsable del pago. El valor es un parámetro del alojamiento (lo edita el administrador y rige de inmediato) y la
 * edad se calcula con la fecha de hoy del reloj del SGA (zona horaria de Colombia).
 */
@Component
public class EdadMinima {

    private final AlojamientoRepository alojamientos;
    private final Clock reloj;

    public EdadMinima(AlojamientoRepository alojamientos, Clock reloj) {
        this.alojamientos = Objects.requireNonNull(alojamientos);
        this.reloj = Objects.requireNonNull(reloj);
    }

    /** Edad mínima vigente, leída del alojamiento en cada consulta */
    public int anios() {
        return alojamientos.obtener()
                .map(a -> a.getEdadMinimaCuenta())
                .filter(Objects::nonNull)
                .orElseThrow(() -> new ReglaDominioException("La edad mínima de la cuenta no está configurada"));
    }

    /** Fecha de hoy en la zona horaria del alojamiento */
    public LocalDate hoy() {
        return LocalDate.now(reloj);
    }

    /** La persona nacida en esa fecha ya cumplió la edad mínima hoy; sin fecha no se puede saber y responde false */
    public boolean laCumple(LocalDate fechaNacimiento) {
        LocalDate hoy = hoy();
        return fechaNacimiento != null && !fechaNacimiento.isAfter(hoy)
                && Period.between(fechaNacimiento, hoy).getYears() >= anios();
    }
}

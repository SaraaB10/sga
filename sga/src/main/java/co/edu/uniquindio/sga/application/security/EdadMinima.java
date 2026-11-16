package co.edu.uniquindio.sga.application.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.Period;
import java.util.Objects;

/**
 * Edad mínima para crear una cuenta y para ser titular de una reserva tomada por la recepción: el titular es el
 * responsable del pago. El valor sale de sga.usuarios.edad-minima y la edad se calcula con la fecha de hoy del
 * reloj del SGA (zona horaria de Colombia).
 */
@Component
public class EdadMinima {

    private final int anios;
    private final Clock reloj;

    public EdadMinima(@Value("${sga.usuarios.edad-minima}") int anios, Clock reloj) {
        if (anios < 0) {
            throw new IllegalArgumentException("sga.usuarios.edad-minima no puede ser negativa");
        }
        this.anios = anios;
        this.reloj = Objects.requireNonNull(reloj);
    }

    public int anios() {
        return anios;
    }

    /** Fecha de hoy en la zona horaria del alojamiento */
    public LocalDate hoy() {
        return LocalDate.now(reloj);
    }

    /** La persona nacida en esa fecha ya cumplió la edad mínima hoy; sin fecha no se puede saber y responde false */
    public boolean laCumple(LocalDate fechaNacimiento) {
        LocalDate hoy = hoy();
        return fechaNacimiento != null && !fechaNacimiento.isAfter(hoy)
                && Period.between(fechaNacimiento, hoy).getYears() >= anios;
    }
}

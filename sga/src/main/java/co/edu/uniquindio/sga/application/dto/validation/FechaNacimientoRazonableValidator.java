package co.edu.uniquindio.sga.application.dto.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Valida @FechaNacimientoRazonable con la fecha de hoy en Colombia (la zona del alojamiento). Un día de diferencia con
 * otra zona no cambia el resultado en la práctica, y así el validador también funciona fuera de Spring.
 */
public class FechaNacimientoRazonableValidator implements ConstraintValidator<FechaNacimientoRazonable, LocalDate> {

    private final Clock reloj;

    public FechaNacimientoRazonableValidator() {
        this(Clock.system(ZoneId.of("America/Bogota")));
    }

    FechaNacimientoRazonableValidator(Clock reloj) {
        this.reloj = reloj;
    }

    @Override
    public boolean isValid(LocalDate fecha, ConstraintValidatorContext contexto) {
        if (fecha == null) {
            return true;
        }
        LocalDate limite = LocalDate.now(reloj).minusYears(FechaNacimientoRazonable.MAXIMO_ANIOS);
        return !fecha.isBefore(limite);
    }
}

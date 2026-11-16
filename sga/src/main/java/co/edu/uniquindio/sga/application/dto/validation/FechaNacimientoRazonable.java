package co.edu.uniquindio.sga.application.dto.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * La fecha de nacimiento no es de hace más de {@value #MAXIMO_ANIOS} años. Que no sea futura lo exigen @Past o
 * @PastOrPresent, y que venga, @NotNull: el null pasa esta regla para que cada error tenga un solo mensaje.
 */
@Documented
@Constraint(validatedBy = FechaNacimientoRazonableValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
public @interface FechaNacimientoRazonable {

    int MAXIMO_ANIOS = 110;

    String MENSAJE = "La fecha de nacimiento no puede ser de hace más de 110 años";

    String message() default MENSAJE;

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}

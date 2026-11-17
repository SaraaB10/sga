package co.edu.uniquindio.sga.application.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.LocalTime;

/**
 * PUT /api/alojamiento/parametros
 * Parámetros del alojamiento que edita el administrador. Todos son obligatorios: el formulario envía la configuración
 * completa. Este DTO revisa la forma y los rangos razonables (400); las reglas del negocio (por ejemplo, el porcentaje
 * del anticipo cuando se exige) las valida el dominio (409). El dinero viaja en pesos enteros.
 */
public record ActualizarParametrosRequest(
        @NotNull(message = "El umbral de edad facturable es obligatorio")
        @Min(value = 0, message = "El umbral de edad facturable debe estar entre 0 y 17 años")
        @Max(value = 17, message = "El umbral de edad facturable debe estar entre 0 y 17 años")
        Integer umbralEdadFacturable,

        @NotNull(message = "El tiempo de preparación es obligatorio")
        @Min(value = 0, message = "El tiempo de preparación debe estar entre 0 y 24 horas")
        @Max(value = 24, message = "El tiempo de preparación debe estar entre 0 y 24 horas")
        Integer tiempoPreparacionHoras,

        @NotNull(message = "El plazo de confirmación es obligatorio")
        @Min(value = 1, message = "El plazo de confirmación debe estar entre 1 y 168 horas")
        @Max(value = 168, message = "El plazo de confirmación debe estar entre 1 y 168 horas")
        Integer plazoConfirmacionHoras,

        @NotNull(message = "La hora límite de no-show es obligatoria")
        LocalTime horaLimiteNoShow,

        @NotNull(message = "La hora de inicio de la recepción nocturna es obligatoria")
        LocalTime horaInicioLlegadaNocturna,

        @NotNull(message = "El recargo de recepción nocturna es obligatorio")
        @Positive(message = "El recargo de recepción nocturna debe ser mayor que cero")
        Long recargoRecepcionNocturna,

        @NotNull(message = "Las noches de la estancia mínima son obligatorias")
        @Min(value = 1, message = "La estancia mínima debe estar entre 1 y 30 noches")
        @Max(value = 30, message = "La estancia mínima debe estar entre 1 y 30 noches")
        Integer estanciaMinimaNoches,

        @NotNull(message = "Indique si la estancia mínima está activa")
        Boolean estanciaMinimaActiva,

        @NotNull(message = "Indique si se exige anticipo")
        Boolean exigeAnticipo,

        @NotNull(message = "El porcentaje del anticipo es obligatorio")
        @Min(value = 0, message = "El porcentaje del anticipo debe estar entre 0 y 100")
        @Max(value = 100, message = "El porcentaje del anticipo debe estar entre 0 y 100")
        Integer porcentajeAnticipo,

        @NotBlank(message = "El concepto del depósito de garantía es obligatorio")
        @Size(max = ReglaTexto.MAXIMO_MOTIVO, message = "El concepto admite máximo 250 caracteres")
        String conceptoDepositoGarantia,

        @NotNull(message = "El depósito de garantía es obligatorio")
        @Positive(message = "El depósito de garantía debe ser mayor que cero")
        Long depositoGarantia,

        @NotNull(message = "Indique si se aceptan mascotas")
        Boolean aceptaMascotas,

        @NotNull(message = "El cargo de aseo por mascota es obligatorio")
        @PositiveOrZero(message = "El cargo de aseo por mascota no puede ser negativo")
        Long cargoAseoMascota,

        @NotNull(message = "La cantidad máxima de mascotas es obligatoria")
        @Min(value = 0, message = "La cantidad máxima de mascotas debe estar entre 0 y 10")
        @Max(value = 10, message = "La cantidad máxima de mascotas debe estar entre 0 y 10")
        Integer maximoMascotas,

        @NotNull(message = "El peso máximo de la mascota es obligatorio")
        @Min(value = 0, message = "El peso máximo de la mascota debe estar entre 0 y 100 kg")
        @Max(value = 100, message = "El peso máximo de la mascota debe estar entre 0 y 100 kg")
        Integer pesoMaximoMascotaKg,

        @NotNull(message = "La edad mínima de la cuenta es obligatoria")
        @Min(value = 14, message = "La edad mínima de la cuenta debe estar entre 14 y 21 años")
        @Max(value = 21, message = "La edad mínima de la cuenta debe estar entre 14 y 21 años")
        Integer edadMinimaCuenta
) {
}

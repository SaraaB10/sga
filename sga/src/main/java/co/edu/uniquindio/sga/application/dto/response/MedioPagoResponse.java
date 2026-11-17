package co.edu.uniquindio.sga.application.dto.response;

/** Medio de pago del catálogo (L-18): su código es el que viaja en los pagos y reportes. */
public record MedioPagoResponse(
        String codigo,
        String nombre,
        boolean activo
) {
}

package co.edu.uniquindio.sga.application.dto.response;

import java.time.LocalDate;
import java.util.List;

/**
 * Respuesta de CU-06, CU-07 y CU-15: la cuenta de la reserva con sus cargos, pagos y saldo (7.7).
 * Incluye el depósito de garantía del Apartahotel Coral (RP-02). Los valores van en pesos enteros.
 *
 * @param depositoReembolsado true cuando ya se devolvió el depósito de garantía (RP-02).
 * @param totalPagado        abonos menos devoluciones al huésped.
 * @param saldo              cargos menos pagos; negativo si hay saldo a favor del titular.
 * @param autorizadoPor      solo si se cerró con saldo por autorización del administrador (CU-07, RN-17).
 * @param motivoAutorizacion solo en ese mismo caso.
 */
public record FolioResponse(
        String codigoReserva,
        LocalDate fechaApertura,
        long depositoGarantia,
        boolean depositoReembolsado,
        List<CargoResponse> cargos,
        List<PagoResponse> pagos,
        long totalCargos,
        long totalPagado,
        long saldo,
        boolean cerrado,
        LocalDate fechaCierre,
        String autorizadoPor,
        String motivoAutorizacion
) {
}
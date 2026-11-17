package co.edu.uniquindio.sga.domain.valueobject;

import co.edu.uniquindio.sga.domain.exception.ReglaDominioException;

import java.time.LocalDate;

/**
 * Movimiento de dinero registrado contra un folio, con medio y fecha (glosario, F-08). Casi siempre es un abono del
 * huésped; también puede ser una devolución al huésped (la del depósito de garantía, RP-02), que se registra pero el
 * sistema no ejecuta: el desembolso ocurre fuera (3.4). El valor siempre es positivo; la devolución resta del total
 * pagado.
 * Reglas: RN-15 (todo movimiento tiene medio y fecha), RN-16 (no se modifica ni se elimina).
 */
public record Pago(Dinero valor, MedioPago medio, LocalDate fecha, boolean devolucion) {

    public Pago {
        if (valor == null || !valor.esPositivo()) {
            throw new ReglaDominioException("RN-15: el valor del pago debe ser mayor que cero");
        }
        if (medio == null) {
            throw new ReglaDominioException("RN-15: todo pago debe indicar su medio de pago");
        }
        if (fecha == null) {
            throw new ReglaDominioException("RN-15: todo pago debe indicar su fecha");
        }
    }

    /** Abono del huésped. */
    public Pago(Dinero valor, MedioPago medio, LocalDate fecha) {
        this(valor, medio, fecha, false);
    }

    /** Devolución al huésped registrada en el folio (no la ejecuta el sistema). */
    public static Pago devolucionDe(Dinero valor, MedioPago medio, LocalDate fecha) {
        return new Pago(valor, medio, fecha, true);
    }

    /** Lo que suma al total pagado: el abono suma y la devolución resta. */
    public Dinero valorNeto() {
        return devolucion ? valor.negar() : valor;
    }
}

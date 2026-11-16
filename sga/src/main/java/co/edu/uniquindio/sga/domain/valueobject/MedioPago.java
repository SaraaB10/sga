package co.edu.uniquindio.sga.domain.valueobject;

import co.edu.uniquindio.sga.domain.exception.ReglaDominioException;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Medio con el que se registra un pago (L-18), identificado por su código: TARJETA_DEBITO_CREDITO,
 * TRANSFERENCIA_BANCARIA u otro que el administrador agregue al catálogo (MedioPagoAceptado). Que el medio esté
 * aceptado y activo lo revisa quien registra el pago; aquí solo vive el formato del código.
 */
public record MedioPago(String codigo) {

    /** Medios aceptados por el Apartahotel Coral en el Anexo A (L-18) */
    public static final MedioPago TARJETA_DEBITO_CREDITO = new MedioPago("TARJETA_DEBITO_CREDITO");
    public static final MedioPago TRANSFERENCIA_BANCARIA = new MedioPago("TRANSFERENCIA_BANCARIA");

    public MedioPago {
        if (codigo == null || codigo.isBlank()) {
            throw new ReglaDominioException("L-18: el medio de pago es obligatorio");
        }
        codigo = codigo.trim().toUpperCase(Locale.ROOT);
        if (!codigo.matches("[A-Z0-9_]{2,40}")) {
            throw new ReglaDominioException("L-18: el código del medio de pago solo admite letras, números y guiones "
                    + "bajos, entre 2 y 40 caracteres");
        }
    }

    /** Código a partir del nombre: "Efectivo en pesos" → EFECTIVO_EN_PESOS (sin tildes ni símbolos) */
    public static MedioPago desdeNombre(String nombre) {
        if (nombre == null || nombre.isBlank()) {
            throw new ReglaDominioException("L-18: el nombre del medio de pago es obligatorio");
        }
        String sinTildes = Normalizer.normalize(nombre.trim(), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        String codigo = sinTildes.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_").replaceAll("^_+|_+$", "");
        return new MedioPago(codigo.length() > 40 ? codigo.substring(0, 40) : codigo);
    }

    @Override
    public String toString() {
        return codigo;
    }
}

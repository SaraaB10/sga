package co.edu.uniquindio.sga.domain.entity;

import co.edu.uniquindio.sga.domain.exception.ReglaDominioException;
import co.edu.uniquindio.sga.domain.valueobject.MedioPago;

import java.util.Objects;

/**
 * Medio de pago que acepta el alojamiento (L-18). El administrador agrega medios y los activa o desactiva: solo los
 * activos se ofrecen al registrar un pago y se muestran al huésped. La eliminación es lógica, así los pagos ya
 * registrados con un medio desactivado conservan su medio (RN-16).
 */
public class MedioPagoAceptado {

    private final MedioPago medio;
    private String nombre;
    private boolean activo;

    private MedioPagoAceptado(MedioPago medio, String nombre, boolean activo) {
        this.medio = medio;
        this.nombre = nombre;
        this.activo = activo;
    }

    /** Medio nuevo y activo. Si no trae código, se toma del nombre */
    public static MedioPagoAceptado crear(MedioPago medio, String nombre) {
        if (nombre == null || nombre.isBlank()) {
            throw new ReglaDominioException("L-18: el nombre del medio de pago es obligatorio");
        }
        MedioPago codigo = medio != null ? medio : MedioPago.desdeNombre(nombre);
        return new MedioPagoAceptado(codigo, nombre.trim(), true);
    }

    public static MedioPagoAceptado reconstruir(MedioPago medio, String nombre, boolean activo) {
        return new MedioPagoAceptado(Objects.requireNonNull(medio), nombre, activo);
    }

    public void activar() {
        if (activo) {
            throw new ReglaDominioException("El medio de pago " + nombre + " ya está activo");
        }
        activo = true;
    }

    public void desactivar() {
        if (!activo) {
            throw new ReglaDominioException("El medio de pago " + nombre + " ya está desactivado");
        }
        activo = false;
    }

    public MedioPago getMedio() { return medio; }
    public String getNombre() { return nombre; }
    public boolean isActivo() { return activo; }

    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof MedioPagoAceptado otro && medio.equals(otro.medio));
    }

    @Override
    public int hashCode() {
        return medio.hashCode();
    }
}

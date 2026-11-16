package co.edu.uniquindio.sga.domain.repository;

import co.edu.uniquindio.sga.domain.exception.ReglaDominioException;
import co.edu.uniquindio.sga.domain.valueobject.CanalOrigen;
import co.edu.uniquindio.sga.domain.valueobject.EstadoReserva;
import co.edu.uniquindio.sga.domain.valueobject.IdentificacionApartamento;

import java.time.LocalDate;
import java.util.Locale;

/**
 * Criterios del listado de reservas (CU-19, 7.5 "Listados"). Todos son opcionales: un campo en null no filtra.
 * Las fechas desde y hasta acotan la fecha de entrada de la estancia, ambas incluidas. busqueda es un texto libre
 * que encuentra la reserva por una parte de su código o del nombre del titular, sin distinguir mayúsculas.
 */
public record FiltroReservas(EstadoReserva estado, IdentificacionApartamento apartamento, CanalOrigen canal,
                             String documentoTitular, LocalDate desde, LocalDate hasta, String busqueda) {

    public FiltroReservas {
        if (desde != null && hasta != null && desde.isAfter(hasta)) {
            throw new ReglaDominioException("7.5: la fecha desde (" + desde + ") no puede ser posterior a la fecha hasta ("
                    + hasta + ")");
        }
        documentoTitular = vacioANull(documentoTitular);
        busqueda = vacioANull(busqueda);
    }

    /** Los mismos criterios, sin búsqueda por código o titular. */
    public FiltroReservas(EstadoReserva estado, IdentificacionApartamento apartamento, CanalOrigen canal,
                          String documentoTitular, LocalDate desde, LocalDate hasta) {
        this(estado, apartamento, canal, documentoTitular, desde, hasta, null);
    }

    /** Sin ningún criterio: todas las reservas. */
    public static FiltroReservas sinFiltro() {
        return new FiltroReservas(null, null, null, null, null, null, null);
    }

    /** La reserva tiene el texto buscado en su código o en el nombre del titular (sin distinguir mayúsculas). */
    public boolean coincideConBusqueda(String codigo, String nombreTitular) {
        if (busqueda == null) {
            return true;
        }
        String texto = busqueda.toLowerCase(Locale.ROOT);
        return (codigo != null && codigo.toLowerCase(Locale.ROOT).contains(texto))
                || (nombreTitular != null && nombreTitular.toLowerCase(Locale.ROOT).contains(texto));
    }

    private static String vacioANull(String texto) {
        return (texto == null || texto.isBlank()) ? null : texto.trim();
    }
}

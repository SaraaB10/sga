package co.edu.uniquindio.sga.domain.valueobject;

import co.edu.uniquindio.sga.domain.exception.ReglaDominioException;

import java.util.List;

/**
 * Filtros opcionales de la búsqueda de apartamentos disponibles (7.5). Un campo null significa "sin ese filtro".
 * Los precios se comparan con el valor estimado de toda la estancia, no con la tarifa de una noche.
 *
 * Los servicios son lo que el apartamento ofrece según su dotación y sus características (por ejemplo "jacuzzi",
 * "cocina" o "vista al mar"). Se pueden pedir varios y se aplican juntos: el apartamento debe ofrecerlos todos.
 */
public record CriterioBusqueda(Integer capacidadMinima, Dinero precioMinimo, Dinero precioMaximo,
                               String caracteristica, List<String> servicios) {

    public CriterioBusqueda {
        if (capacidadMinima != null && capacidadMinima <= 0) {
            throw new ReglaDominioException("7.5: la capacidad mínima debe ser mayor que cero");
        }
        if (precioMinimo != null && precioMinimo.esNegativo()) {
            throw new ReglaDominioException("7.5: el precio mínimo no puede ser negativo");
        }
        if (precioMaximo != null && precioMaximo.esNegativo()) {
            throw new ReglaDominioException("7.5: el precio máximo no puede ser negativo");
        }
        if (precioMinimo != null && precioMaximo != null && !precioMaximo.esMayorOIgualQue(precioMinimo)) {
            throw new ReglaDominioException("7.5: el precio mínimo no puede ser mayor que el precio máximo");
        }
        caracteristica = caracteristica == null || caracteristica.isBlank() ? null : caracteristica.trim();
        servicios = servicios == null ? List.of() : servicios.stream()
                .filter(servicio -> servicio != null && !servicio.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
    }

    /** Búsqueda sin filtro de servicios. */
    public CriterioBusqueda(Integer capacidadMinima, Dinero precioMinimo, Dinero precioMaximo, String caracteristica) {
        this(capacidadMinima, precioMinimo, precioMaximo, caracteristica, List.of());
    }

    /** Búsqueda sin filtros adicionales: solo estancia y ocupantes. */
    public static CriterioBusqueda sinFiltros() {
        return new CriterioBusqueda(null, null, null, null, List.of());
    }

    public boolean cumpleCapacidad(Capacidad capacidad) {
        return capacidadMinima == null || capacidad.maximo() >= capacidadMinima;
    }

    public boolean cumpleCaracteristica(Dotacion dotacion) {
        return caracteristica == null || dotacion.mencionaCaracteristica(caracteristica);
    }

    /**
     * Cada servicio pedido lo ofrece algún elemento de la dotación o la descripción del apartamento: deben aparecer
     * todas sus palabras, en cualquier orden, sin distinguir mayúsculas ni tildes ("vista al mar" coincide con
     * "Vista frontal al mar Caribe"). Sin servicios pedidos siempre se cumple.
     */
    public boolean cumpleServicios(Dotacion dotacion, String descripcion) {
        return servicios.stream().allMatch(servicio ->
                dotacion.ofreceServicio(servicio) || Dotacion.textoMenciona(descripcion, servicio));
    }

    /** El valor estimado de la estancia debe quedar dentro del rango [precioMinimo, precioMaximo]. */
    public boolean cumplePrecio(Dinero valorEstimado) {
        boolean sobreElMinimo = precioMinimo == null || valorEstimado.esMayorOIgualQue(precioMinimo);
        boolean bajoElMaximo = precioMaximo == null || precioMaximo.esMayorOIgualQue(valorEstimado);
        return sobreElMinimo && bajoElMaximo;
    }

    public boolean filtraPorPrecio() {
        return precioMinimo != null || precioMaximo != null;
    }
}

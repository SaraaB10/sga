package co.edu.uniquindio.sga.domain.valueobject;

import co.edu.uniquindio.sga.domain.exception.ReglaDominioException;

import java.util.List;

/**
 * Filtros opcionales de la búsqueda de apartamentos disponibles (7.5). Un campo null significa "sin ese filtro".
 * Los precios se comparan con el valor estimado de toda la estancia, no con la tarifa de una noche.
 *
 * La característica es cómo es el apartamento como espacio (por ejemplo "2 dormitorios"); los servicios son lo que
 * trae su dotación (por ejemplo "Cocina integral abierta"). Se pueden pedir varios servicios y se aplican juntos: el
 * apartamento debe ofrecerlos todos.
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

    /**
     * La característica pedida es una de las del apartamento (Apartamento.getCaracteristicas, por ejemplo
     * "2 dormitorios"), sin distinguir mayúsculas ni tildes. Sin característica pedida siempre se cumple.
     */
    public boolean cumpleCaracteristica(List<String> caracteristicas) {
        return caracteristica == null || (caracteristicas != null && caracteristicas.stream()
                .anyMatch(c -> Dotacion.normalizar(c).equals(Dotacion.normalizar(caracteristica))));
    }

    /**
     * Cada servicio pedido lo ofrece algún elemento de la dotación: deben aparecer todas sus palabras, en cualquier
     * orden, sin distinguir mayúsculas ni tildes ("vista al mar" coincide con "Vista frontal al mar Caribe"). Las
     * opciones del filtro son los elementos reales de las dotaciones. Sin servicios pedidos siempre se cumple.
     */
    public boolean cumpleServicios(Dotacion dotacion) {
        return servicios.stream().allMatch(dotacion::ofreceServicio);
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

package co.edu.uniquindio.sga.domain.valueobject;

import co.edu.uniquindio.sga.domain.exception.ReglaDominioException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Matriz de trazabilidad: 7.5 (filtros de la búsqueda), implementada en CriterioBusqueda y Dotacion. */
class CriterioBusquedaTest {

    @Test
    @DisplayName("7.5: el precio mínimo no puede superar al máximo y la capacidad mínima debe ser positiva")
    void rechazarFiltrosInconsistentes() {
        ReglaDominioException precios = assertThrows(ReglaDominioException.class,
                () -> new CriterioBusqueda(null, Dinero.de(900_000), Dinero.de(500_000), null));
        assertTrue(precios.getMessage().startsWith("7.5"));
        assertThrows(ReglaDominioException.class, () -> new CriterioBusqueda(0, null, null, null));
        assertThrows(ReglaDominioException.class, () -> new CriterioBusqueda(null, Dinero.de(-1), null, null));
    }

    @Test
    @DisplayName("7.5: los filtros vacíos no excluyen nada y el rango de precio incluye sus extremos")
    void aplicarFiltros() {
        CriterioBusqueda sinFiltros = new CriterioBusqueda(null, null, null, "  ");
        assertNull(sinFiltros.caracteristica());
        assertTrue(sinFiltros.cumpleCapacidad(new Capacidad(1)));
        assertTrue(sinFiltros.cumplePrecio(Dinero.de(5_000_000)));

        CriterioBusqueda rango = new CriterioBusqueda(4, Dinero.de(500_000), Dinero.de(900_000), null);
        assertTrue(rango.cumpleCapacidad(new Capacidad(4)));
        assertFalse(rango.cumpleCapacidad(new Capacidad(2)));
        assertTrue(rango.cumplePrecio(Dinero.de(500_000)));
        assertTrue(rango.cumplePrecio(Dinero.de(900_000)));
        assertFalse(rango.cumplePrecio(Dinero.de(900_001)));
    }

    @Test
    @DisplayName("7.5: la característica es una de las del apartamento, sin distinguir mayúsculas ni tildes")
    void buscarCaracteristicaDelApartamento() {
        List<String> caracteristicas = List.of("2 dormitorios");

        assertTrue(new CriterioBusqueda(null, null, null, "2 dormitorios").cumpleCaracteristica(caracteristicas));
        assertTrue(new CriterioBusqueda(null, null, null, " 2 DORMITORIOS ").cumpleCaracteristica(caracteristicas));
        assertFalse(new CriterioBusqueda(null, null, null, "1 dormitorio").cumpleCaracteristica(caracteristicas));
        // La característica no se busca en la dotación: eso es el filtro de servicios
        assertFalse(new CriterioBusqueda(null, null, null, "hidromasaje").cumpleCaracteristica(caracteristicas));
        assertTrue(CriterioBusqueda.sinFiltros().cumpleCaracteristica(caracteristicas));
    }

    @Test
    @DisplayName("Servicios: varios se aplican juntos y se buscan solo en la dotación")
    void filtrarPorVariosServicios() {
        Dotacion dotacion = new Dotacion(List.of("Balcón con tina de hidromasaje privada", "Cocina integral"));

        assertTrue(servicios("hidromasaje").cumpleServicios(dotacion));
        assertTrue(servicios("COCINA", "Balcon").cumpleServicios(dotacion));
        assertTrue(servicios("Cocina integral").cumpleServicios(dotacion));                  // un elemento completo
        assertFalse(servicios("cocina", "piscina").cumpleServicios(dotacion));              // falta uno: no cumple
        assertFalse(servicios("bahia").cumpleServicios(dotacion));                          // no está en la dotación
    }

    @Test
    @DisplayName("Servicios: se buscan todas las palabras en un mismo elemento, en cualquier orden")
    void servicioDeVariasPalabras() {
        Dotacion dotacion = new Dotacion(List.of("Vista frontal al mar Caribe", "Cocina integral"));

        assertTrue(servicios("Vista al mar").cumpleServicios(dotacion));
        assertTrue(servicios("mar vista").cumpleServicios(dotacion));
        // "vista" y "cocina" están en elementos distintos: no es un servicio "vista cocina"
        assertFalse(servicios("vista cocina").cumpleServicios(dotacion));
    }

    @Test
    @DisplayName("Servicios: sin servicios, o solo vacíos, no se excluye nada y los repetidos cuentan una vez")
    void serviciosVaciosNoFiltran() {
        Dotacion dotacion = new Dotacion(List.of("Cocina integral"));

        assertTrue(CriterioBusqueda.sinFiltros().cumpleServicios(dotacion));
        assertTrue(new CriterioBusqueda(null, null, null, null, null).servicios().isEmpty());
        CriterioBusqueda conVacios = servicios(" ", "", " cocina ", "cocina");
        assertTrue(conVacios.cumpleServicios(dotacion));
        assertTrue(conVacios.servicios().equals(List.of("cocina")));
    }

    private static CriterioBusqueda servicios(String... servicios) {
        return new CriterioBusqueda(null, null, null, null, java.util.Arrays.asList(servicios));
    }
}

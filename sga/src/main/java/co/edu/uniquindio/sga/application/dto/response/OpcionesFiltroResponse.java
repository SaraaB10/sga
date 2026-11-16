package co.edu.uniquindio.sga.application.dto.response;

import java.util.List;

/**
 * GET /api/apartamentos/filtros: opciones de los filtros de la búsqueda (7.5), con los valores reales de los
 * apartamentos activos. Características: cómo es el apartamento (por ejemplo "2 dormitorios"). Servicios: los
 * elementos de su dotación (por ejemplo "Cocina integral abierta").
 */
public record OpcionesFiltroResponse(
        List<String> caracteristicas,
        List<String> servicios
) {
}

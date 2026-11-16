package co.edu.uniquindio.sga.application.dto.response;

import java.util.List;

/**
 * GET /api/apartamentos/filtros: opciones de los filtros de la búsqueda (7.5), con los valores reales de los
 * apartamentos activos. Dormitorios: los números que existen (1, 2, 3). Características: rasgos del apartamento (por
 * ejemplo "Vista al mar"). Servicios: los elementos de su dotación (por ejemplo "Cocina integral abierta").
 */
public record OpcionesFiltroResponse(
        List<Integer> dormitorios,
        List<String> caracteristicas,
        List<String> servicios
) {
}

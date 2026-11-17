package co.edu.uniquindio.sga.application.dto.response;

import co.edu.uniquindio.sga.domain.valueobject.GravedadNovedad;
import co.edu.uniquindio.sga.domain.valueobject.OrigenSugerencia;
import co.edu.uniquindio.sga.domain.valueobject.SugerenciaGravedad;

/**
 * Sugerencia de gravedad de la IA (10.2). origen dice si la dio Gemini (IA) o las reglas locales (LOCAL, también el
 * respaldo cuando Gemini falla). Si no hay sugerencia, disponible es false y gravedad, explicacion y origen vienen en
 * null; el formulario sigue funcionando igual.
 */
public record SugerenciaGravedadResponse(
        boolean disponible,
        GravedadNovedad gravedad,
        String explicacion,
        OrigenSugerencia origen
) {

    public static SugerenciaGravedadResponse de(SugerenciaGravedad sugerencia) {
        return new SugerenciaGravedadResponse(true, sugerencia.gravedad(), sugerencia.explicacion(),
                sugerencia.origen());
    }

    public static SugerenciaGravedadResponse noDisponible() {
        return new SugerenciaGravedadResponse(false, null, null, null);
    }
}

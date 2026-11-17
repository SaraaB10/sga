package co.edu.uniquindio.sga.domain.valueobject;

/**
 * De dónde salió una sugerencia de gravedad: del modelo de IA (Gemini) o de las reglas locales por palabras clave,
 * que también son el respaldo cuando Gemini falla. La pantalla lo muestra para que la persona sepa qué la ayudó.
 */
public enum OrigenSugerencia {
    IA,
    LOCAL
}

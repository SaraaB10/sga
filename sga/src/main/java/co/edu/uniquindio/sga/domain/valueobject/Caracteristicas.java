package co.edu.uniquindio.sga.domain.valueobject;

import co.edu.uniquindio.sga.domain.exception.ReglaDominioException;

import java.util.ArrayList;
import java.util.List;

/**
 * Características del apartamento (7.3): rasgos del espacio y de su ubicación, por ejemplo "Vista al mar", "Frente a
 * la playa", "Balcón", "Terraza", "Piso alto" o "Admite mascotas". Son distintas de la dotación (Dotacion), que es lo
 * que trae el apartamento (hidromasaje, cocina, cava de vinos). Pueden ser ninguna; no se repiten (sin distinguir
 * mayúsculas ni tildes) y cada una tiene entre 2 y 60 caracteres.
 */
public record Caracteristicas(List<String> elementos) {

    public static final int MAXIMO = 20;
    public static final int LARGO_MAXIMO = 60;

    public Caracteristicas {
        List<String> limpias = new ArrayList<>();
        for (String elemento : elementos == null ? List.<String>of() : elementos) {
            if (elemento == null || elemento.isBlank()) {
                throw new ReglaDominioException("7.3: las características no pueden estar vacías");
            }
            String limpia = elemento.trim();
            if (limpia.length() < 2 || limpia.length() > LARGO_MAXIMO) {
                throw new ReglaDominioException("7.3: cada característica debe tener entre 2 y " + LARGO_MAXIMO
                        + " caracteres");
            }
            boolean repetida = limpias.stream()
                    .anyMatch(c -> Dotacion.normalizar(c).equals(Dotacion.normalizar(limpia)));
            if (repetida) {
                throw new ReglaDominioException("7.3: la característica " + limpia + " está repetida");
            }
            limpias.add(limpia);
        }
        if (limpias.size() > MAXIMO) {
            throw new ReglaDominioException("7.3: el apartamento admite máximo " + MAXIMO + " características");
        }
        elementos = List.copyOf(limpias);
    }

    public static Caracteristicas ninguna() {
        return new Caracteristicas(List.of());
    }

    /** Tiene la característica, sin distinguir mayúsculas ni tildes */
    public boolean contiene(String caracteristica) {
        if (caracteristica == null || caracteristica.isBlank()) {
            return false;
        }
        String buscada = Dotacion.normalizar(caracteristica);
        return elementos.stream().anyMatch(c -> Dotacion.normalizar(c).equals(buscada));
    }
}

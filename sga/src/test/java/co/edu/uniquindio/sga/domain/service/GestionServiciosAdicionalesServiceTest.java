package co.edu.uniquindio.sga.domain.service;

import co.edu.uniquindio.sga.domain.entity.ServicioAdicional;
import co.edu.uniquindio.sga.domain.exception.ReglaDominioException;
import co.edu.uniquindio.sga.domain.valueobject.Dinero;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Gestión de los servicios adicionales del alojamiento (L-17) y la protección de RP-03: el servicio que cobra la
 * recepción nocturna no se desactiva ni deja de generar cargo.
 */
class GestionServiciosAdicionalesServiceTest {

    private static final String NOCTURNA = "SRV-RECEPCION-NOCTURNA";

    private final EscenarioCoral escenario = new EscenarioCoral();
    private final GestionServiciosAdicionalesService gestion =
            new GestionServiciosAdicionalesService(escenario.alojamientos, NOCTURNA);

    @BeforeEach
    void agregarRecepcionNocturna() {
        escenario.alojamiento.agregarServicioAdicional(ServicioAdicional.crearConCargo(NOCTURNA,
                "Recepción nocturna", "Llegadas desde las 20:00", Dinero.de(70_000)));
        escenario.alojamientos.guardar(escenario.alojamiento);
    }

    @Test
    @DisplayName("Crear un servicio con cargo: queda activo, con id en mayúsculas y guardado en el alojamiento")
    void crearServicioConCargo() {
        ServicioAdicional spa = gestion.registrar(" srv-spa ", "Spa", "  ", true, Dinero.de(180_000));

        assertEquals("SRV-SPA", spa.getId());
        assertTrue(spa.isActivo());
        assertTrue(spa.isGeneraCargo());
        assertNull(spa.getDescripcion());
        assertTrue(escenario.alojamientos.obtener().orElseThrow().buscarServicioAdicional("SRV-SPA").isPresent());
    }

    @Test
    @DisplayName("Crear: se rechaza un id repetido, un id con espacios y un servicio con cargo sin valor")
    void rechazarServiciosInvalidos() {
        assertThrows(ReglaDominioException.class,
                () -> gestion.registrar(NOCTURNA, "Otra", null, true, Dinero.de(1_000)));
        assertThrows(ReglaDominioException.class,
                () -> gestion.registrar("SRV MAL", "Mal id", null, false, null));
        assertThrows(ReglaDominioException.class,
                () -> gestion.registrar("SRV-CERO", "Sin valor", null, true, Dinero.cero()));
    }

    @Test
    @DisplayName("Editar: un servicio sin cargo queda en cero y uno con cargo exige valor mayor que cero")
    void editarServicio() {
        gestion.registrar("SRV-SPA", "Spa", null, true, Dinero.de(180_000));

        ServicioAdicional sinCargo = gestion.actualizar("SRV-SPA", "Spa de cortesía", "Incluido", false, Dinero.de(5));
        assertFalse(sinCargo.isGeneraCargo());
        assertEquals(Dinero.cero(), sinCargo.getValor());
        assertEquals("Spa de cortesía", sinCargo.getNombre());

        assertThrows(ReglaDominioException.class, () -> gestion.actualizar("SRV-SPA", "Spa", null, true, null));
    }

    @Test
    @DisplayName("Desactivar y activar: eliminación lógica, sin repetir el mismo estado")
    void desactivarYActivar() {
        gestion.registrar("SRV-SPA", "Spa", null, true, Dinero.de(180_000));

        assertFalse(gestion.desactivar("SRV-SPA").isActivo());
        assertThrows(ReglaDominioException.class, () -> gestion.desactivar("SRV-SPA"));
        assertTrue(gestion.activar("SRV-SPA").isActivo());
        assertThrows(ReglaDominioException.class, () -> gestion.activar("SRV-SPA"));
    }

    @Test
    @DisplayName("RP-03: la recepción nocturna no se desactiva ni se deja sin cargo, pero su valor sí cambia")
    void recepcionNocturnaProtegida() {
        ReglaDominioException desactivar = assertThrows(ReglaDominioException.class, () -> gestion.desactivar(NOCTURNA));
        ReglaDominioException sinCargo = assertThrows(ReglaDominioException.class,
                () -> gestion.actualizar(NOCTURNA, "Recepción nocturna", null, false, null));

        assertTrue(desactivar.getMessage().startsWith("RP-03"));
        assertTrue(sinCargo.getMessage().startsWith("RP-03"));
        assertEquals(Dinero.de(75_000),
                gestion.actualizar(NOCTURNA, "Recepción nocturna", null, true, Dinero.de(75_000)).getValor());
        assertTrue(gestion.motivoObligatorio(NOCTURNA).isPresent());
        assertTrue(gestion.motivoObligatorio("SRV-OTRO").isEmpty());
    }
}

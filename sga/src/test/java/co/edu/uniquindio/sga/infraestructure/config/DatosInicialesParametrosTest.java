package co.edu.uniquindio.sga.infraestructure.config;

import co.edu.uniquindio.sga.domain.entity.Alojamiento;
import co.edu.uniquindio.sga.domain.repository.AlojamientoRepository;
import co.edu.uniquindio.sga.domain.valueobject.Coordenada;
import co.edu.uniquindio.sga.domain.valueobject.EstanciaMinima;
import co.edu.uniquindio.sga.domain.valueobject.PlazoConfirmacion;
import co.edu.uniquindio.sga.domain.valueobject.TiempoPreparacion;
import co.edu.uniquindio.sga.domain.valueobject.UmbralEdadFacturable;
import co.edu.uniquindio.sga.infrastructure.config.DatosInicialesParametrosConfig;
import co.edu.uniquindio.sga.infrastructure.persistence.inmemory.AlojamientoRepositoryInMemory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.CommandLineRunner;

import java.math.BigDecimal;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * El cargador de los parámetros revisa sus propios datos: una base creada antes de ellos los recibe al arrancar, y lo
 * que ya tenía valor (pudo cambiarlo el administrador) se conserva.
 */
class DatosInicialesParametrosTest {

    private final AlojamientoRepository alojamientos = new AlojamientoRepositoryInMemory();

    @Test
    @DisplayName("Un alojamiento sin parámetros recibe todos los valores iniciales y el servicio de recepción nocturna")
    void completaUnaBaseSinParametros() throws Exception {
        alojamientos.guardar(alojamientoSinParametros());

        cargador().run();

        Alojamiento a = alojamientos.obtener().orElseThrow();
        assertTrue(a.tieneParametrosConfigurados());
        assertEquals(6, a.getUmbralEdadFacturable().anios());
        assertEquals(50, a.getPorcentajeAnticipo());
        assertEquals(0, new BigDecimal(250_000).compareTo(a.getDepositoGarantia().monto().monto()));
        assertEquals(18, a.getEdadMinimaCuenta());
        assertTrue(a.getPoliticaMascotas().aplica());
        assertEquals(0, new BigDecimal(70_000).compareTo(
                a.obtenerServicioAdicional("SRV-RECEPCION-NOCTURNA").getValor().monto()));
    }

    @Test
    @DisplayName("Los parámetros que ya tenían valor se conservan; solo se completan los que faltan")
    void conservaLosValoresGuardados() throws Exception {
        Alojamiento existente = alojamientoSinParametros();
        existente.configurarParametros(new UmbralEdadFacturable(10), new TiempoPreparacion(4), new PlazoConfirmacion(24),
                LocalTime.of(21, 0), LocalTime.of(19, 0), new EstanciaMinima(3, false), true, 30);
        alojamientos.guardar(existente);

        cargador().run();

        Alojamiento a = alojamientos.obtener().orElseThrow();
        assertEquals(10, a.getUmbralEdadFacturable().anios());
        assertEquals(30, a.getPorcentajeAnticipo());
        assertEquals(LocalTime.of(19, 0), a.getHoraInicioLlegadaNocturna());
        assertEquals(18, a.getEdadMinimaCuenta(), "La edad mínima faltaba: toma el valor inicial");
        assertTrue(a.getDepositoGarantia() != null, "El depósito faltaba: toma el valor inicial");
    }

    private CommandLineRunner cargador() {
        CommandLineRunner sinAlojamientoNuevo = args -> { };
        return new DatosInicialesParametrosConfig().cargarParametrosAlojamiento(sinAlojamientoNuevo, alojamientos,
                6, 3, 12, LocalTime.of(22, 0), LocalTime.of(20, 0), 2, true, true, 50, true, 110_000, 2, 10,
                "Depósito de garantía", 250_000, 18, "SRV-RECEPCION-NOCTURNA", 70_000);
    }

    private static Alojamiento alojamientoSinParametros() {
        return Alojamiento.crear("ALO-1", "Coral", "Apartamentos", "Cartagena", "Bocagrande",
                new Coordenada(10.40, -75.55), LocalTime.of(15, 0), LocalTime.of(11, 0), "Normas");
    }
}

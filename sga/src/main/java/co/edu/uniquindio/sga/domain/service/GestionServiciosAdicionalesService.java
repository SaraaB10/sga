package co.edu.uniquindio.sga.domain.service;

import co.edu.uniquindio.sga.domain.entity.Alojamiento;
import co.edu.uniquindio.sga.domain.entity.ServicioAdicional;
import co.edu.uniquindio.sga.domain.exception.ReglaDominioException;
import co.edu.uniquindio.sga.domain.repository.AlojamientoRepository;
import co.edu.uniquindio.sga.domain.valueobject.Dinero;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Administra los servicios adicionales que ofrece el alojamiento (L-17): crearlos, editar su nombre, descripción,
 * si generan cargo y su valor, y activarlos o desactivarlos. La eliminación es lógica: un servicio desactivado deja
 * de ofrecerse, pero los cargos que ya generó siguen en los folios (RN-16).
 *
 * Reglas: RP-03 (el servicio con el que se cobra el recargo de llegada nocturna no puede desactivarse ni dejar de
 * generar cargo, porque toda reserva con llegada nocturna lo necesita).
 */
public class GestionServiciosAdicionalesService {

    /** Identificación estable del servicio: letras mayúsculas, números y guiones, por ejemplo SRV-SPA. */
    private static final Pattern FORMATO_ID = Pattern.compile("^[A-Z0-9][A-Z0-9-]{1,39}$");

    private final AlojamientoRepository alojamientoRepository;
    private final String idServicioRecepcionNocturna;

    /** @param idServicioRecepcionNocturna id del servicio que cobra el recargo de RP-03; puede ser null. */
    public GestionServiciosAdicionalesService(AlojamientoRepository alojamientoRepository,
                                              String idServicioRecepcionNocturna) {
        this.alojamientoRepository = Objects.requireNonNull(alojamientoRepository);
        this.idServicioRecepcionNocturna = idServicioRecepcionNocturna;
    }

    /** Crea un servicio nuevo, activo. El id se guarda en mayúsculas y no puede repetirse. */
    public ServicioAdicional registrar(String id, String nombre, String descripcion, boolean generaCargo, Dinero valor) {
        if (id == null || id.isBlank()) {
            throw new ReglaDominioException("El id del servicio es obligatorio");
        }
        String idNormalizado = id.trim().toUpperCase(Locale.ROOT);
        if (!FORMATO_ID.matcher(idNormalizado).matches()) {
            throw new ReglaDominioException("El id del servicio solo admite letras, números y guiones (por ejemplo "
                    + "SRV-SPA), entre 2 y 40 caracteres");
        }
        Alojamiento alojamiento = obtenerAlojamiento();
        if (alojamiento.buscarServicioAdicional(idNormalizado).isPresent()) {
            throw new ReglaDominioException("Ya existe el servicio adicional " + idNormalizado);
        }
        String descripcionLimpia = descripcion == null || descripcion.isBlank() ? null : descripcion.trim();
        ServicioAdicional servicio = generaCargo
                ? ServicioAdicional.crearConCargo(idNormalizado, nombre, descripcionLimpia, valor)
                : ServicioAdicional.crearSinCargo(idNormalizado, nombre, descripcionLimpia);
        alojamiento.agregarServicioAdicional(servicio);
        alojamientoRepository.guardar(alojamiento);
        return servicio;
    }

    /** RP-03: el servicio de recepción nocturna debe seguir generando cargo. */
    public ServicioAdicional actualizar(String id, String nombre, String descripcion, boolean generaCargo,
                                        Dinero valor) {
        Alojamiento alojamiento = obtenerAlojamiento();
        ServicioAdicional servicio = alojamiento.obtenerServicioAdicional(id);
        if (!generaCargo && esRecepcionNocturna(servicio)) {
            throw new ReglaDominioException("RP-03: El servicio " + servicio.getNombre() + " cobra el recargo de las "
                    + "llegadas nocturnas y debe generar cargo");
        }
        servicio.actualizar(nombre, descripcion, generaCargo, valor);
        alojamientoRepository.guardar(alojamiento);
        return servicio;
    }

    /** Eliminación lógica. RP-03: el servicio de recepción nocturna no se desactiva. */
    public ServicioAdicional desactivar(String id) {
        Alojamiento alojamiento = obtenerAlojamiento();
        ServicioAdicional servicio = alojamiento.obtenerServicioAdicional(id);
        if (esRecepcionNocturna(servicio)) {
            throw new ReglaDominioException("RP-03: El servicio " + servicio.getNombre() + " no se puede desactivar: "
                    + "cobra el recargo de las llegadas nocturnas");
        }
        if (!servicio.isActivo()) {
            throw new ReglaDominioException("El servicio " + servicio.getNombre() + " ya está desactivado");
        }
        servicio.desactivar();
        alojamientoRepository.guardar(alojamiento);
        return servicio;
    }

    public ServicioAdicional activar(String id) {
        Alojamiento alojamiento = obtenerAlojamiento();
        ServicioAdicional servicio = alojamiento.obtenerServicioAdicional(id);
        if (servicio.isActivo()) {
            throw new ReglaDominioException("El servicio " + servicio.getNombre() + " ya está activo");
        }
        servicio.activar();
        alojamientoRepository.guardar(alojamiento);
        return servicio;
    }

    public ServicioAdicional consultar(String id) {
        return obtenerAlojamiento().obtenerServicioAdicional(id);
    }

    /**
     * Por qué un servicio no puede desactivarse ni dejar de generar cargo, o vacío si se puede. Lo muestra la
     * pantalla de administración para explicarlo antes de que el administrador lo intente.
     */
    public Optional<String> motivoObligatorio(String id) {
        if (id != null && id.equals(idServicioRecepcionNocturna)) {
            return Optional.of("Se cobra automáticamente a las reservas con llegada nocturna (RP-03). Puedes cambiar "
                    + "su nombre, su descripción y su valor, pero no desactivarlo ni dejarlo sin cargo.");
        }
        return Optional.empty();
    }

    private boolean esRecepcionNocturna(ServicioAdicional servicio) {
        return servicio.getId().equals(idServicioRecepcionNocturna);
    }

    private Alojamiento obtenerAlojamiento() {
        return alojamientoRepository.obtener()
                .orElseThrow(() -> new ReglaDominioException("F-13: No hay un alojamiento configurado"));
    }
}

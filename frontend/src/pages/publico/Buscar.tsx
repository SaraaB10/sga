import { useState, type FormEvent } from 'react'
import { useSearchParams } from 'react-router-dom'
import { CalendarSearch, SearchX } from 'lucide-react'
import { z } from 'zod'
import { useApartamentosDisponibles, useOpcionesFiltro } from '@/api/publico'
import type { FiltrosDisponibles } from '@/api/tipos'
import { AvisoError, Campo, Dinero, Fecha, Paginador, Vacio } from '@/components/coral'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Skeleton } from '@/components/ui/skeleton'
import { cn } from '@/lib/utils'
import { textoOpcional, useValidacion } from '@/lib/validacion'
import { BuscadorEstancia } from './comun/BuscadorEstancia'
import { useReglasAlojamiento, useTemporadasAlojamiento } from './comun/consultas'
import { EncabezadoFiltros } from './comun/EncabezadoFiltros'
import { claseContenidoPlegable, claseSelect } from './comun/estilos'
import {
  estanciaAParams,
  fechasCompletas,
  grupoSinAdultos,
  leerEstancia,
  MENSAJE_SIN_ADULTOS,
  resumenHuespedes,
} from './comun/estancia'
import { avisoEstanciaMinima, nochesEntre } from './comun/reglas'
import { TarjetaApartamento } from './comun/TarjetaApartamento'
import { MapaAlojamiento } from './MapaAlojamiento'

const FILTROS = [
  'capacidadMinima',
  'dormitorios',
  'caracteristica',
  'servicios',
  'precioMinimo',
  'precioMaximo',
] as const
type Filtro = (typeof FILTROS)[number]

/** En la URL los servicios van separados por coma: servicios=Hidromasaje,Cocina */
const aServicios = (texto: string | null) =>
  (texto ?? '')
    .split(',')
    .map((s) => s.trim())
    .filter((s) => s !== '')

const aNumero = (texto: string | null) => {
  const limpio = (texto ?? '').replace(/[$.\s]/g, '')
  return limpio === '' || Number.isNaN(Number(limpio)) ? undefined : Number(limpio)
}

export default function Buscar() {
  const [params, setParams] = useSearchParams()
  const { alojamiento, reglas } = useReglasAlojamiento()
  const { temporadas } = useTemporadasAlojamiento()
  const estancia = leerEstancia(params)
  const pagina = Number(params.get('pagina') ?? 0) || 0
  const noches = nochesEntre(estancia.entrada, estancia.salida)

  const avisoRP01 = avisoEstanciaMinima(estancia.entrada, estancia.salida, temporadas, reglas)
  const sinAdultos = grupoSinAdultos(estancia.huespedes, reglas.umbralEdad, estancia.entrada)
  // Sin el umbral de edad no se sabe quién es menor: no se busca hasta tener las reglas del alojamiento
  const reglasCargadas = !!alojamiento.data

  // La URL es la fuente de verdad; React Query compara los filtros por valor
  const filtros: FiltrosDisponibles | null =
    !reglasCargadas || noches < 1 || avisoRP01 || sinAdultos
      ? null
      : {
          entrada: estancia.entrada,
          salida: estancia.salida,
          ocupantes: estancia.huespedes.length,
          fechasNacimiento: fechasCompletas(estancia.huespedes),
          capacidadMinima: aNumero(params.get('capacidadMinima')),
          dormitorios: aNumero(params.get('dormitorios')),
          caracteristica: params.get('caracteristica')?.trim() || undefined,
          servicios: aServicios(params.get('servicios')),
          precioMinimo: aNumero(params.get('precioMinimo')),
          precioMaximo: aNumero(params.get('precioMaximo')),
        }

  const consulta = useApartamentosDisponibles(filtros, pagina)

  const filtrosActuales = Object.fromEntries(FILTROS.map((f) => [f, params.get(f) ?? ''])) as Record<Filtro, string>

  const cambiarPagina = (nueva: number) => {
    const siguientes = new URLSearchParams(params)
    siguientes.set('pagina', String(nueva))
    setParams(siguientes)
    window.scrollTo({ top: 0, behavior: 'smooth' })
  }

  const enlaceDetalle = (id: string) =>
    `/apartamentos/${encodeURIComponent(id)}?${estanciaAParams(estancia).toString()}`

  return (
    <section className="flex flex-col gap-6">
      <div>
        <h1 className="font-titulo text-3xl font-semibold md:text-4xl">Apartamentos disponibles</h1>
        {noches > 0 && (
          <p className="mt-1 text-sm text-muted-foreground">
            <Fecha valor={estancia.entrada} /> → <Fecha valor={estancia.salida} /> · {noches}{' '}
            {noches === 1 ? 'noche' : 'noches'} ·{' '}
            <span className="whitespace-nowrap">
              {resumenHuespedes(estancia.huespedes, reglas.umbralEdad, estancia.entrada)}
            </span>
          </p>
        )}
      </div>

      <BuscadorEstancia
        key={`${estancia.entrada}|${estancia.salida}|${estancia.huespedes.join(',')}`}
        inicial={estancia}
        umbralEdad={reglas.umbralEdad}
        textoBoton="Actualizar"
        alBuscar={(nueva) => setParams(estanciaAParams(nueva, filtrosActuales))}
      />

      <div className="grid gap-6 lg:grid-cols-[17rem_1fr] lg:items-start">
        <FiltrosBusqueda
          key={params.toString()}
          valores={filtrosActuales}
          alAplicar={(valores) => setParams(estanciaAParams(estancia, valores))}
        />

        <div className="flex flex-col gap-4">
          {avisoRP01 && <AvisoError titulo="Estancia mínima de fin de semana" mensaje={avisoRP01} />}
          {sinAdultos && noches > 0 && <AvisoError titulo="Falta un adulto" mensaje={`${MENSAJE_SIN_ADULTOS}.`} />}

          {noches < 1 ? (
            <div className="rounded-tarjeta border border-border bg-card">
              <Vacio
                icono={CalendarSearch}
                titulo="Elige tus fechas"
                descripcion="Indica la llegada, la salida y los huéspedes para ver los apartamentos libres y el valor de tu estancia."
              />
            </div>
          ) : avisoRP01 || sinAdultos ? null : consulta.error ? (
            <AvisoError error={consulta.error} />
          ) : !consulta.data ? (
            <div className="grid gap-5 sm:grid-cols-2">
              {[0, 1, 2, 3].map((i) => (
                <Skeleton key={i} className="h-80" />
              ))}
            </div>
          ) : consulta.data.contenido.length === 0 ? (
            <div className="rounded-tarjeta border border-border bg-card">
              <Vacio
                icono={SearchX}
                titulo="No hay apartamentos libres"
                descripcion="Prueba con otras fechas o quita algunos filtros."
              />
            </div>
          ) : (
            <>
              <p className="text-sm text-muted-foreground" aria-live="polite">
                {consulta.data.totalElementos}{' '}
                {consulta.data.totalElementos === 1 ? 'apartamento disponible' : 'apartamentos disponibles'}
              </p>
              <div className="grid gap-5 sm:grid-cols-2">
                {consulta.data.contenido.map((a) => (
                  <TarjetaApartamento
                    key={a.identificacion}
                    enlace={enlaceDetalle(a.identificacion)}
                    foto={a.imagenPrincipal}
                    identificacion={a.identificacion}
                    nombre={a.nombre}
                    capacidad={a.capacidadMaxima}
                    precio={
                      <span className="flex flex-col">
                        <span className="text-xs text-muted-foreground">Valor estimado de la estancia</span>
                        <Dinero valor={a.valorEstimado} className="font-titulo text-xl font-semibold text-coral-texto" />
                      </span>
                    }
                  />
                ))}
              </div>
              <Paginador
                pagina={pagina}
                totalPaginas={consulta.data.totalPaginas}
                totalElementos={consulta.data.totalElementos}
                alCambiar={cambiarPagina}
              />
            </>
          )}
        </div>
      </div>

      <MapaAlojamiento variante="franja" />
    </section>
  )
}

interface PropsFiltros {
  valores: Record<Filtro, string>
  alAplicar: (valores: Record<Filtro, string>) => void
}

/** Valor en pesos opcional de un filtro: vacío o entero de 0 en adelante */
const pesosFiltro = z
  .string()
  .trim()
  .refine((v) => v === '' || /^\d+$/.test(v.replace(/[$.\s]/g, '')), 'Escribe un valor en pesos, sin decimales')

// Filtros de GET /api/apartamentos/disponibles: capacidad entera de 1 o más y el precio mínimo no pasa del máximo
const esquemaFiltros = z
  .object({
    capacidadMinima: z
      .string()
      .trim()
      .refine((v) => v === '' || (/^\d+$/.test(v) && Number(v) >= 1), 'Escribe una capacidad entera de 1 o más'),
    dormitorios: z
      .string()
      .trim()
      .refine((v) => v === '' || (/^\d+$/.test(v) && Number(v) >= 1), 'Elige un número de dormitorios'),
    caracteristica: textoOpcional(60, 'La característica admite máximo 60 caracteres'),
    servicios: z.string(),
    precioMinimo: pesosFiltro,
    precioMaximo: pesosFiltro,
  })
  .superRefine((datos, contexto) => {
    const min = aNumero(datos.precioMinimo)
    const max = aNumero(datos.precioMaximo)
    if (min !== undefined && max !== undefined && min > max)
      contexto.addIssue({
        code: 'custom',
        path: ['precioMaximo'],
        message: 'El precio máximo no puede ser menor que el mínimo.',
      })
  })

/**
 * Capacidad mínima, dormitorios, característica del apartamento, servicios de su dotación y rango del valor estimado.
 * Las opciones de dormitorios, características y servicios salen de los apartamentos activos
 * (GET /api/apartamentos/filtros).
 */
function FiltrosBusqueda({ valores, alAplicar }: PropsFiltros) {
  const opciones = useOpcionesFiltro()
  const [borrador, setBorrador] = useState(valores)
  const validacion = useValidacion(esquemaFiltros, borrador)
  const errores = validacion.errores
  // En el celular los filtros empiezan plegados para que los resultados queden a la vista
  const [abierto, setAbierto] = useState(false)

  const cambiar = (campo: Filtro, valor: string) => setBorrador((b) => ({ ...b, [campo]: valor }))
  const serviciosElegidos = aServicios(borrador.servicios)
  const alternarServicio = (servicio: string, elegido: boolean) =>
    cambiar(
      'servicios',
      (elegido ? [...serviciosElegidos, servicio] : serviciosElegidos.filter((s) => s !== servicio)).join(','),
    )

  const aplicar = (e: FormEvent) => {
    e.preventDefault()
    if (validacion.comprobar()) alAplicar(borrador)
  }

  const limpiar = () => {
    const vacios = {
      capacidadMinima: '',
      dormitorios: '',
      caracteristica: '',
      servicios: '',
      precioMinimo: '',
      precioMaximo: '',
    }
    setBorrador(vacios)
    validacion.reiniciar()
    alAplicar(vacios)
  }

  return (
    <form
      noValidate
      onSubmit={aplicar}
      aria-label="Filtros"
      className="flex flex-col gap-4 rounded-tarjeta border border-border bg-card p-4 shadow-tarjeta lg:p-5"
    >
      <EncabezadoFiltros
        id="boton-filtros"
        controla="contenido-filtros"
        abierto={abierto}
        alCambiar={setAbierto}
        activos={Object.values(valores).filter((v) => v.trim() !== '').length}
        desde="lg"
      />
      <div id="contenido-filtros" className={claseContenidoPlegable(abierto, 'lg')}>
      <Campo id="filtro-capacidad" etiqueta="Capacidad mínima" error={errores.capacidadMinima}>
        <Input
          type="number"
          inputMode="numeric"
          min={1}
          placeholder="Ej.: 4"
          value={borrador.capacidadMinima}
          onChange={(e) => cambiar('capacidadMinima', e.target.value)}
        />
      </Campo>
      <Campo id="filtro-dormitorios" etiqueta="Dormitorios" error={errores.dormitorios}>
        <select
          className={claseSelect}
          value={borrador.dormitorios}
          onChange={(e) => cambiar('dormitorios', e.target.value)}
        >
          <option value="">Cualquiera</option>
          {/* Un número de la URL que ya no tiene ningún apartamento sigue visible para poder quitarlo */}
          {borrador.dormitorios && !opciones.data?.dormitorios.map(String).includes(borrador.dormitorios) && (
            <option value={borrador.dormitorios}>{borrador.dormitorios}</option>
          )}
          {(opciones.data?.dormitorios ?? []).map((d) => (
            <option key={d} value={String(d)}>
              {d} {d === 1 ? 'dormitorio' : 'dormitorios'}
            </option>
          ))}
        </select>
      </Campo>
      <Campo
        id="filtro-caracteristica"
        etiqueta="Características"
        ayuda="Rasgos del apartamento: vista, balcón, terraza, si admite mascotas…"
        error={errores.caracteristica}
      >
        <select
          className={claseSelect}
          value={borrador.caracteristica}
          onChange={(e) => cambiar('caracteristica', e.target.value)}
        >
          <option value="">Cualquiera</option>
          {/* Una característica de la URL que ya no ofrece ningún apartamento sigue visible para poder quitarla */}
          {borrador.caracteristica && !opciones.data?.caracteristicas.includes(borrador.caracteristica) && (
            <option value={borrador.caracteristica}>{borrador.caracteristica}</option>
          )}
          {(opciones.data?.caracteristicas ?? []).map((c) => (
            <option key={c} value={c}>
              {c}
            </option>
          ))}
        </select>
      </Campo>
      <fieldset className="flex flex-col gap-2">
        <legend className="mb-1.5 text-sm font-medium">Servicios</legend>
        <p className="-mt-1 text-xs text-muted-foreground">
          Lo que trae la dotación del apartamento. Muestra los que tienen todos los que elijas.
        </p>
        {opciones.error && <AvisoError error={opciones.error} titulo="No se pudieron cargar los servicios" />}
        {!opciones.data && !opciones.error && <Skeleton className="h-16" />}
        <div className="flex flex-wrap gap-2">
          {(opciones.data?.servicios ?? []).map((servicio) => {
            const elegido = serviciosElegidos.includes(servicio)
            return (
              <label
                key={servicio}
                className={cn(
                  'flex cursor-pointer items-center gap-1.5 rounded-full border px-3 py-1.5 text-xs transition-colors has-[:focus-visible]:ring-3 has-[:focus-visible]:ring-ring/50',
                  elegido ? 'border-coral bg-crema font-semibold' : 'border-border bg-card hover:bg-muted',
                )}
              >
                <input
                  type="checkbox"
                  className="sr-only"
                  checked={elegido}
                  onChange={(e) => alternarServicio(servicio, e.target.checked)}
                />
                {servicio}
              </label>
            )
          })}
        </div>
      </fieldset>
      <fieldset className="flex flex-col gap-2">
        <legend className="mb-1.5 text-sm font-medium">Valor de la estancia</legend>
        <div className="grid grid-cols-2 gap-2">
          <Campo id="filtro-precio-min" etiqueta="Desde" error={errores.precioMinimo}>
            <Input
              inputMode="numeric"
              placeholder="$0"
              value={borrador.precioMinimo}
              onChange={(e) => cambiar('precioMinimo', e.target.value)}
            />
          </Campo>
          <Campo id="filtro-precio-max" etiqueta="Hasta" error={errores.precioMaximo}>
            <Input
              inputMode="numeric"
              placeholder="Sin límite"
              value={borrador.precioMaximo}
              onChange={(e) => cambiar('precioMaximo', e.target.value)}
            />
          </Campo>
        </div>
      </fieldset>
      <div className="flex gap-2">
        <Button type="submit" className="flex-1">
          Aplicar
        </Button>
        <Button type="button" variant="ghost" onClick={limpiar}>
          Limpiar
        </Button>
      </div>
      </div>
    </form>
  )
}

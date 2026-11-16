import { useState } from 'react'
import { ChevronLeft, ChevronRight } from 'lucide-react'
import { useDisponibilidadApartamento } from '@/api/publico'
import { AvisoError, formatearFecha } from '@/components/coral'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { cn } from '@/lib/utils'
import { useReglasAlojamiento } from './comun/consultas'
import { hoyISO } from './comun/reglas'

/** Meses hacia adelante que se pueden consultar desde el mes actual */
const MESES_ADELANTE = 12
const DIAS_SEMANA = ['lu', 'ma', 'mi', 'ju', 'vi', 'sá', 'do']

/** Fondo de las noches libres de viernes y sábado; el mismo tono del calendario del panel */
const CLASE_FIN_DE_SEMANA = 'bg-sol/40'

/** Viernes o sábado: las noches de fin de semana */
function esNocheDeFinDeSemana(anio: number, mes: number, dia: number): boolean {
  const diaSemana = new Date(anio, mes, dia).getDay()
  return diaSemana === 5 || diaSemana === 6
}

/** Primer día del mes en formato YYYY-MM-DD */
function inicioDeMes(anio: number, mes: number): string {
  const fecha = new Date(anio, mes, 1)
  return `${fecha.getFullYear()}-${String(fecha.getMonth() + 1).padStart(2, '0')}-01`
}

/**
 * Calendario mensual de disponibilidad del apartamento: cada noche aparece libre u ocupada, sin decir por qué.
 * Se navega mes a mes, desde el mes actual hasta doce meses adelante.
 */
export function CalendarioDisponibilidad({ identificacion }: { identificacion: string }) {
  const hoy = hoyISO()
  const [anioHoy, mesHoy] = hoy.split('-').map(Number)
  const [desplazamiento, setDesplazamiento] = useState(0)

  const anio = new Date(anioHoy, mesHoy - 1 + desplazamiento, 1).getFullYear()
  const mes = new Date(anioHoy, mesHoy - 1 + desplazamiento, 1).getMonth()
  const desde = inicioDeMes(anio, mes)
  const hasta = inicioDeMes(anio, mes + 1)
  const diasDelMes = new Date(anio, mes + 1, 0).getDate()
  // Lunes primero: getDay() da 0 para el domingo
  const huecosAntes = (new Date(anio, mes, 1).getDay() + 6) % 7

  const consulta = useDisponibilidadApartamento(identificacion, desde, hasta)
  const { reglas } = useReglasAlojamiento()
  const libres = new Map((consulta.data?.noches ?? []).map((n) => [n.fecha, n.libre]))
  const titulo = formatearFecha(desde, "MMMM 'de' yyyy")

  return (
    <section aria-labelledby="titulo-disponibilidad">
      <h2 id="titulo-disponibilidad" className="font-titulo text-2xl font-semibold">
        Disponibilidad
      </h2>
      <p className="mt-1 text-sm text-muted-foreground">Cada casilla es una noche. Elige noches libres para tu estancia.</p>

      <div className="mt-4 max-w-md rounded-tarjeta border border-border bg-card p-4">
        <div className="flex items-center justify-between gap-2">
          <Button
            type="button"
            variant="ghost"
            size="icon"
            aria-label="Mes anterior"
            disabled={desplazamiento === 0}
            onClick={() => setDesplazamiento((d) => d - 1)}
          >
            <ChevronLeft aria-hidden="true" />
          </Button>
          <p className="font-semibold first-letter:uppercase" aria-live="polite">
            {titulo}
          </p>
          <Button
            type="button"
            variant="ghost"
            size="icon"
            aria-label="Mes siguiente"
            disabled={desplazamiento >= MESES_ADELANTE}
            onClick={() => setDesplazamiento((d) => d + 1)}
          >
            <ChevronRight aria-hidden="true" />
          </Button>
        </div>

        {consulta.error ? (
          <AvisoError error={consulta.error} className="mt-3" />
        ) : !consulta.data ? (
          <Skeleton className="mt-3 h-64 w-full" />
        ) : (
          <div
            role="grid"
            aria-label={`Disponibilidad de ${titulo}`}
            className={cn('mt-3 grid grid-cols-7 gap-1 text-center text-sm', consulta.isFetching && 'opacity-60')}
          >
            {DIAS_SEMANA.map((d) => (
              <div key={d} role="columnheader" className="pb-1 text-xs font-semibold text-muted-foreground uppercase">
                {d}
              </div>
            ))}
            {Array.from({ length: huecosAntes }, (_, i) => (
              <div key={`hueco-${i}`} aria-hidden="true" />
            ))}
            {Array.from({ length: diasDelMes }, (_, i) => {
              const fecha = `${desde.slice(0, 8)}${String(i + 1).padStart(2, '0')}`
              const pasada = fecha < hoy
              const libre = libres.get(fecha) === true
              const estado = pasada ? 'pasada' : libre ? 'libre' : 'ocupada'
              // Viernes y sábado: en temporada media o alta piden la estancia mínima
              const finDeSemana = estado === 'libre' && esNocheDeFinDeSemana(anio, mes, i + 1)
              const textoEstado = estado === 'libre' ? 'libre' : estado === 'ocupada' ? 'no disponible' : 'ya pasó'
              return (
                <div
                  key={fecha}
                  role="gridcell"
                  aria-label={`${formatearFecha(fecha, "EEEE d 'de' MMMM")}: ${textoEstado}${finDeSemana ? ', noche de fin de semana' : ''}${fecha === hoy ? ', hoy' : ''}`}
                  data-estado={estado}
                  data-fin-de-semana={finDeSemana || undefined}
                  className={cn(
                    'flex aspect-square items-center justify-center rounded-md border text-sm',
                    estado === 'libre' && 'border-coral/40 font-semibold text-foreground',
                    estado === 'libre' && (finDeSemana ? CLASE_FIN_DE_SEMANA : 'bg-crema'),
                    estado === 'ocupada' && 'border-transparent bg-muted text-muted-foreground line-through',
                    estado === 'pasada' && 'border-transparent text-muted-foreground/50',
                    fecha === hoy && 'ring-2 ring-coral/60',
                  )}
                >
                  {i + 1}
                </div>
              )
            })}
          </div>
        )}

        <h3 id="leyenda-disponibilidad" className="mt-4 text-xs font-semibold text-foreground">
          Qué significa cada color
        </h3>
        <ul
          aria-labelledby="leyenda-disponibilidad"
          className="mt-2 grid gap-x-4 gap-y-2 text-xs text-muted-foreground sm:grid-cols-2"
        >
          <li className="flex items-center gap-2">
            <span aria-hidden="true" className="size-4 shrink-0 rounded-sm border border-coral/40 bg-crema" />
            Libre: se puede reservar
          </li>
          <li className="flex items-center gap-2">
            <span aria-hidden="true" className={cn('size-4 shrink-0 rounded-sm border border-coral/40', CLASE_FIN_DE_SEMANA)} />
            Noches de fin de semana (viernes y sábado)
          </li>
          <li className="flex items-center gap-2">
            <span
              aria-hidden="true"
              className="flex size-4 shrink-0 items-center justify-center rounded-sm bg-muted text-[0.6rem] leading-none line-through"
            >
              9
            </span>
            No disponible: ya reservada o bloqueada
          </li>
          <li className="flex items-center gap-2">
            <span
              aria-hidden="true"
              className="flex size-4 shrink-0 items-center justify-center rounded-sm text-[0.6rem] leading-none text-muted-foreground/50"
            >
              9
            </span>
            Ya pasó
          </li>
          <li className="flex items-center gap-2">
            <span aria-hidden="true" className="size-4 shrink-0 rounded-sm ring-2 ring-coral/60" />
            Hoy
          </li>
        </ul>
        {reglas.estanciaMinimaActiva && reglas.estanciaMinima !== null && (
          <p className="mt-2 text-xs text-muted-foreground">
            En temporada media y alta, una estancia que incluye una noche de viernes o sábado debe ser de mínimo{' '}
            {reglas.estanciaMinima} noches.
          </p>
        )}
      </div>
    </section>
  )
}

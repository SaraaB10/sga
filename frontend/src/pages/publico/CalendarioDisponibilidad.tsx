import { useState } from 'react'
import { ChevronLeft, ChevronRight } from 'lucide-react'
import { useDisponibilidadApartamento } from '@/api/publico'
import { AvisoError, formatearFecha } from '@/components/coral'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { cn } from '@/lib/utils'
import { hoyISO } from './comun/reglas'

/** Meses hacia adelante que se pueden consultar desde el mes actual */
const MESES_ADELANTE = 12
const DIAS_SEMANA = ['lu', 'ma', 'mi', 'ju', 'vi', 'sá', 'do']

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
              return (
                <div
                  key={fecha}
                  role="gridcell"
                  aria-label={`${formatearFecha(fecha, "EEEE d 'de' MMMM")}: ${estado === 'libre' ? 'libre' : estado === 'ocupada' ? 'no disponible' : 'ya pasó'}`}
                  data-estado={estado}
                  className={cn(
                    'flex aspect-square items-center justify-center rounded-md border text-sm',
                    estado === 'libre' && 'border-coral/40 bg-crema font-semibold text-foreground',
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

        <ul aria-label="Convenciones" className="mt-4 flex flex-wrap gap-x-4 gap-y-2 text-xs text-muted-foreground">
          <li className="flex items-center gap-1.5">
            <span aria-hidden="true" className="size-3 rounded-sm border border-coral/40 bg-crema" /> Libre
          </li>
          <li className="flex items-center gap-1.5">
            <span aria-hidden="true" className="size-3 rounded-sm bg-muted" /> No disponible
          </li>
        </ul>
      </div>
    </section>
  )
}

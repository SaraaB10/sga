import { useState } from 'react'
import { ChevronLeft, ChevronRight, X } from 'lucide-react'
import { useDisponibilidadApartamento } from '@/api/publico'
import { AvisoError, formatearFecha } from '@/components/coral'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'
import { RAYAS_BLOQUEO } from './estilos'
import { hoyISO, nochesEntre, sumarDias } from './reglas'

const DIAS_SEMANA = ['lu', 'ma', 'mi', 'ju', 'vi', 'sá', 'do']
/** Meses hacia adelante que se pueden recorrer desde el mes actual */
const MESES_ADELANTE = 12
/** Noches que se consultan desde la llegada para saber hasta dónde se puede alargar la estancia */
const NOCHES_DESDE_LLEGADA = 62

type EstadoNoche = 'libre' | 'ocupada' | 'bloqueada' | 'desconocida'

/** Primer día del mes, "2026-11-01" */
function inicioDeMes(anio: number, mes: number): string {
  const fecha = new Date(anio, mes, 1)
  return `${fecha.getFullYear()}-${String(fecha.getMonth() + 1).padStart(2, '0')}-01`
}

/** Meses entre el mes de hoy y el de la fecha (0 si es este mes) */
function mesesHasta(fecha: string, hoy: string): number {
  if (!fecha) return 0
  const [a1, m1] = hoy.split('-').map(Number)
  const [a2, m2] = fecha.split('-').map(Number)
  return Math.min(MESES_ADELANTE, Math.max(0, (a2 - a1) * 12 + (m2 - m1)))
}

interface Props {
  /** Sin apartamento no se conocen las noches ocupadas: solo se impiden las fechas pasadas */
  idApartamento?: string
  entrada: string
  /** '' mientras se elige el día de salida */
  salida: string
  alCambiar: (entrada: string, salida: string) => void
  /** Al modificar una reserva: sus noches cuentan como libres */
  excluirReserva?: string
  errorEntrada?: string
  errorSalida?: string
  className?: string
}

/**
 * Selector de la estancia en un calendario mensual. Con el apartamento elegido marca cada noche como libre, ocupada o
 * bloqueada (GET /api/apartamentos/{id}/disponibilidad) y no deja elegirlas: la llegada debe ser una noche libre y la
 * salida, un día después de la llegada sin noches ocupadas en medio (el día de salida puede estar ocupado por la
 * reserva siguiente). Primer clic: llegada; segundo clic: salida.
 */
export function SelectorEstancia({
  idApartamento,
  entrada,
  salida,
  alCambiar,
  excluirReserva,
  errorEntrada,
  errorSalida,
  className,
}: Props) {
  const hoy = hoyISO()
  const [anioHoy, mesHoy] = hoy.split('-').map(Number)
  const [desplazamiento, setDesplazamiento] = useState(() => mesesHasta(entrada, hoy))

  const anio = new Date(anioHoy, mesHoy - 1 + desplazamiento, 1).getFullYear()
  const mes = new Date(anioHoy, mesHoy - 1 + desplazamiento, 1).getMonth()
  const desde = inicioDeMes(anio, mes)
  const hasta = inicioDeMes(anio, mes + 1)
  const diasDelMes = new Date(anio, mes + 1, 0).getDate()
  const huecosAntes = (new Date(anio, mes, 1).getDay() + 6) % 7

  // Noches del mes que se ve y, con la llegada elegida, las 62 siguientes a ella (para el límite de la salida)
  const delMes = useDisponibilidadApartamento(idApartamento, desde, hasta, excluirReserva)
  const desdeLlegada = useDisponibilidadApartamento(
    idApartamento,
    entrada && entrada >= hoy ? entrada : '',
    entrada && entrada >= hoy ? sumarDias(entrada, NOCHES_DESDE_LLEGADA) : '',
    excluirReserva,
  )
  const noches = new Map<string, EstadoNoche>()
  if (idApartamento) {
    for (const n of [...(desdeLlegada.data?.noches ?? []), ...(delMes.data?.noches ?? [])]) {
      noches.set(n.fecha, n.libre ? 'libre' : n.bloqueada ? 'bloqueada' : 'ocupada')
    }
  }
  const estadoDe = (fecha: string): EstadoNoche => noches.get(fecha) ?? 'desconocida'

  // Eligiendo la salida: el primer día con la noche ocupada es el último día de salida posible
  const eligiendoSalida = !!entrada && !salida
  let limiteSalida: string | null = null
  if (eligiendoSalida && idApartamento && desdeLlegada.data) {
    for (let i = 0; i < NOCHES_DESDE_LLEGADA; i++) {
      const noche = sumarDias(entrada, i)
      const estado = estadoDe(noche)
      if (estado === 'ocupada' || estado === 'bloqueada') {
        limiteSalida = noche
        break
      }
    }
  }

  // Estancia elegida con alguna noche que ya no está libre (por ejemplo, venía de la URL o cambió el apartamento)
  const nochesElegidas = entrada && salida ? Array.from({ length: nochesEntre(entrada, salida) }, (_, i) => sumarDias(entrada, i)) : []
  const conflicto = nochesElegidas.some((n) => estadoDe(n) === 'ocupada' || estadoDe(n) === 'bloqueada')

  const elegir = (fecha: string) => {
    if (eligiendoSalida && fecha > entrada) alCambiar(entrada, fecha)
    else alCambiar(fecha, '')
  }

  const titulo = formatearFecha(desde, "MMMM 'de' yyyy")
  const consultaConError = delMes.error ?? desdeLlegada.error
  const totalNoches = nochesEntre(entrada, salida)

  return (
    <div className={cn('flex flex-col gap-3', className)}>
      <div className="grid gap-3 sm:grid-cols-2">
        <ResumenFecha id="estancia-llegada" etiqueta="Llegada" fecha={entrada} error={errorEntrada} />
        <ResumenFecha
          id="estancia-salida"
          etiqueta="Salida"
          fecha={salida}
          error={errorSalida}
          vacio={entrada ? 'Elige el día de salida' : undefined}
        />
      </div>

      <div className="w-full max-w-md rounded-tarjeta border border-border bg-card p-3">
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
        <p className="mt-1 text-center text-xs text-muted-foreground" aria-live="polite">
          {eligiendoSalida
            ? `Ahora elige el día de salida${limiteSalida ? ` (hasta el ${formatearFecha(limiteSalida, "d 'de' MMMM")})` : ''}.`
            : 'Elige la noche de llegada.'}
          {!idApartamento && ' Elige el apartamento para ver qué noches están libres.'}
        </p>

        {consultaConError && <AvisoError error={consultaConError} className="mt-2" />}

        <div
          role="grid"
          aria-label={`Estancia: ${titulo}`}
          className={cn('mt-2 grid grid-cols-7 gap-1 text-center text-sm', delMes.isFetching && 'opacity-60')}
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
            const estado = estadoDe(fecha)
            const nocheTomada = estado === 'ocupada' || estado === 'bloqueada'
            const comoSalida = eligiendoSalida && fecha > entrada
            const habilitado = pasada
              ? false
              : comoSalida
                ? limiteSalida === null || fecha <= limiteSalida
                : !nocheTomada
            const esExtremo = fecha === entrada || fecha === salida
            const enMedio = !!entrada && !!salida && fecha > entrada && fecha < salida
            // Lo que se pinta: un día pasado se ve como pasado aunque la API lo dé como no libre
            const visual: EstadoNoche | 'pasada' = pasada && estado !== 'bloqueada' ? 'pasada' : estado
            const textoEstado = pasada
              ? 'ya pasó'
              : estado === 'ocupada'
                ? 'no disponible'
                : estado === 'bloqueada'
                  ? 'bloqueada'
                  : estado === 'libre'
                    ? 'libre'
                    : ''
            return (
              <div key={fecha} role="gridcell" aria-selected={esExtremo || enMedio}>
                <button
                  type="button"
                  disabled={!habilitado}
                  data-fecha={fecha}
                  data-estado={visual}
                  aria-label={`${formatearFecha(fecha, "EEEE d 'de' MMMM")}${textoEstado ? `: ${textoEstado}` : ''}${fecha === entrada ? ', llegada' : ''}${fecha === salida ? ', salida' : ''}${comoSalida && habilitado && nocheTomada ? ', solo como día de salida' : ''}`}
                  onClick={() => elegir(fecha)}
                  className={cn(
                    'flex aspect-square w-full items-center justify-center rounded-md border text-sm transition-colors outline-none focus-visible:ring-3 focus-visible:ring-ring/50',
                    habilitado && 'cursor-pointer hover:border-coral',
                    !habilitado && 'cursor-not-allowed',
                    visual === 'libre' && !esExtremo && !enMedio && 'border-coral/40 bg-crema font-semibold',
                    // Libre pero fuera de alcance (después de una noche ocupada, eligiendo la salida)
                    visual === 'libre' && !habilitado && 'opacity-40',
                    visual === 'ocupada' &&
                      !esExtremo &&
                      'border-dashed border-muted-foreground/40 bg-transparent text-muted-foreground line-through',
                    visual === 'bloqueada' && !esExtremo && 'border-transparent font-medium',
                    visual === 'desconocida' && !esExtremo && !enMedio && 'border-border',
                    visual === 'pasada' && 'border-transparent bg-transparent text-muted-foreground/50',
                    enMedio && !nocheTomada && 'border-coral/40 bg-coral/15',
                    esExtremo && 'border-coral bg-coral font-semibold text-white',
                    fecha === hoy && !esExtremo && 'ring-2 ring-coral/60',
                  )}
                  style={visual === 'bloqueada' && !esExtremo ? RAYAS_BLOQUEO : undefined}
                >
                  {i + 1}
                </button>
              </div>
            )
          })}
        </div>

        <ul className="mt-3 flex flex-wrap gap-x-4 gap-y-1.5 text-xs text-muted-foreground" aria-label="Qué significa cada color">
          <li className="flex items-center gap-1.5">
            <span aria-hidden="true" className="size-3.5 rounded-sm border border-coral/40 bg-crema" /> Libre
          </li>
          <li className="flex items-center gap-1.5">
            <span aria-hidden="true" className="size-3.5 rounded-sm border border-dashed border-muted-foreground/40" /> No
            disponible
          </li>
          <li className="flex items-center gap-1.5">
            <span aria-hidden="true" className="size-3.5 rounded-sm" style={RAYAS_BLOQUEO} /> Bloqueada
          </li>
          <li className="flex items-center gap-1.5">
            <span aria-hidden="true" className="size-3.5 rounded-sm bg-coral" /> Tu estancia
          </li>
        </ul>
      </div>

      <div className="flex flex-wrap items-center justify-between gap-2 text-sm">
        <span className="text-muted-foreground">
          {totalNoches > 0 ? `${totalNoches} ${totalNoches === 1 ? 'noche' : 'noches'}` : ''}
        </span>
        {(entrada || salida) && (
          <Button type="button" variant="ghost" size="sm" onClick={() => alCambiar('', '')}>
            <X aria-hidden="true" /> Borrar fechas
          </Button>
        )}
      </div>

      {conflicto && (
        <AvisoError
          titulo="Noches no disponibles"
          mensaje="Algunas noches de la estancia elegida ya no están libres en este apartamento. Elige otras fechas."
        />
      )}
    </div>
  )
}

function ResumenFecha({
  id,
  etiqueta,
  fecha,
  error,
  vacio,
}: {
  id: string
  etiqueta: string
  fecha: string
  error?: string
  vacio?: string
}) {
  return (
    <div className="flex flex-col gap-1">
      <span id={id} className="text-sm font-medium">
        {etiqueta}
      </span>
      <output
        aria-labelledby={id}
        aria-invalid={error ? true : undefined}
        className={cn(
          'flex min-h-11 items-center rounded-campo border px-3 text-sm',
          error ? 'border-error' : 'border-border',
          !fecha && 'text-muted-foreground',
        )}
      >
        {fecha ? formatearFecha(fecha, "EEEE d 'de' MMMM yyyy") : (vacio ?? 'Elige en el calendario')}
      </output>
      {error && <p className="text-xs text-error">{error}</p>}
    </div>
  )
}

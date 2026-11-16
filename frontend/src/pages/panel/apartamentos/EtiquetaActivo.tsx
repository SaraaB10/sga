import type { ApartamentoDetalleResponse, BloqueoResponse } from '@/api/tipos'
import { formatearFecha } from '@/components/coral'
import { Skeleton } from '@/components/ui/skeleton'
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip'
import { cn } from '@/lib/utils'
import { hoyISO, sumarDias } from '../calendario/fechas'

/** Bloqueo vigente que retira hoy de la venta: hoy está entre su primera noche y el día en que vuelve (excluido) */
function bloqueoDeHoy(bloqueos: BloqueoResponse[], hoy = hoyISO()): BloqueoResponse | undefined {
  return bloqueos
    .filter((b) => b.vigente && b.fechaInicio <= hoy && hoy < b.fechaFin)
    .sort((a, b) => b.fechaFin.localeCompare(a.fechaFin))[0]
}

const PILDORA = 'inline-flex rounded-full px-2.5 py-1 text-xs font-semibold whitespace-nowrap'

/**
 * Estado de venta del apartamento, en UNA sola etiqueta:
 * Retirado (eliminación lógica), Inactivo (aún no se activa), Bloqueado hasta <última noche> (bloqueo vigente hoy,
 * con el motivo en un tooltip), "A la venta · sin bloqueo" (fuera de servicio pero sin bloqueo: se sigue vendiendo)
 * o "A la venta". Mientras carga el detalle, un esqueleto.
 */
export function EtiquetaActivo({ apartamento }: { apartamento: ApartamentoDetalleResponse | undefined }) {
  if (!apartamento) return <Skeleton className="ml-auto h-5 w-20 md:ml-0" />

  if (apartamento.eliminado) {
    return <span className={cn(PILDORA, 'border border-border text-muted-foreground')}>Retirado</span>
  }
  if (!apartamento.activo) {
    return <span className={cn(PILDORA, 'border border-border text-muted-foreground')}>Inactivo</span>
  }
  const bloqueo = bloqueoDeHoy(apartamento.bloqueosVigentes)
  if (bloqueo) {
    const ultimaNoche = sumarDias(bloqueo.fechaFin, -1)
    return (
      <Tooltip>
        <TooltipTrigger asChild>
          {/* Enfocable para que el motivo también se lea con el teclado */}
          <span
            tabIndex={0}
            className={cn(PILDORA, 'cursor-help bg-[#4A3542] text-white outline-none focus-visible:ring-3 focus-visible:ring-ring/50')}
            aria-label={`Bloqueado hasta el ${formatearFecha(ultimaNoche, "d 'de' MMMM")}. Motivo: ${bloqueo.motivo}`}
          >
            Bloqueado hasta {formatearFecha(ultimaNoche, 'd MMM')}
          </span>
        </TooltipTrigger>
        <TooltipContent>
          {bloqueo.motivo} · vuelve a la venta el {formatearFecha(bloqueo.fechaFin, "d 'de' MMMM")}
        </TooltipContent>
      </Tooltip>
    )
  }
  if (apartamento.estadoOperativo === 'FUERA_DE_SERVICIO') {
    return <span className={cn(PILDORA, 'bg-[#FFF1C2] text-[#6B4800]')}>A la venta · sin bloqueo</span>
  }
  return <span className={cn(PILDORA, 'bg-[#DDF2EA] text-[#1C5E45]')}>A la venta</span>
}

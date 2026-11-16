import { useEffect, useId, useRef, useState } from 'react'
import { format, parseISO, subYears, addDays } from 'date-fns'
import { ChevronDown, Minus, Plus, Trash2, Users } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { cn } from '@/lib/utils'
import { MENSAJE_SIN_ADULTOS, resumenHuespedes, separarGrupo } from './estancia'
import { esFechaValida, esMenorNoPaga, hoyISO } from './reglas'

interface Props {
  /** Fecha de nacimiento de cada huésped ('' si no se indicó: cuenta como adulto) */
  huespedes: string[]
  alCambiar: (huespedes: string[]) => void
  umbralEdad: number | null
  /** Fecha con la que se calcula la edad (la llegada); por defecto, hoy */
  referencia?: string
  maximo?: number
  /** Muestra un botón con el resumen que abre la lista; si no, la lista va siempre visible */
  desplegable?: boolean
  className?: string
}

/**
 * Composición del grupo: un contador de adultos (pagan noche, mínimo 1) y, aparte, los menores del umbral de edad,
 * cada uno con su fecha de nacimiento (no pagan noche, pero ocupan cupo). El grupo siempre tiene al menos un adulto:
 * el titular es siempre un ocupante que paga.
 */
export function SelectorHuespedes({
  huespedes,
  alCambiar,
  umbralEdad,
  referencia,
  maximo,
  desplegable = false,
  className,
}: Props) {
  const id = useId()
  const [abierto, setAbierto] = useState(false)
  const [errorMenor, setErrorMenor] = useState<{ indice: number; texto: string } | null>(null)
  const contenedor = useRef<HTMLDivElement>(null)

  // Cierra el desplegable al hacer clic afuera o con Escape
  useEffect(() => {
    if (!desplegable || !abierto) return
    const alClic = (e: MouseEvent) => {
      if (contenedor.current && !contenedor.current.contains(e.target as Node)) setAbierto(false)
    }
    const alTecla = (e: KeyboardEvent) => e.key === 'Escape' && setAbierto(false)
    document.addEventListener('mousedown', alClic)
    document.addEventListener('keydown', alTecla)
    return () => {
      document.removeEventListener('mousedown', alClic)
      document.removeEventListener('keydown', alTecla)
    }
  }, [desplegable, abierto])

  const { adultos, menores } = separarGrupo(huespedes, umbralEdad, referencia)
  const lleno = maximo !== undefined && huespedes.length >= maximo
  const base = parseISO(esFechaValida(referencia) ? referencia : hoyISO())
  // Un menor nació después de esta fecha: aún no cumple el umbral el día de la llegada
  const nacidoDespuesDe = umbralEdad !== null ? format(addDays(subYears(base, umbralEdad), 1), 'yyyy-MM-dd') : null
  // Fecha propuesta al agregar un menor: un año menos que el umbral
  const fechaPropuesta =
    umbralEdad !== null ? format(subYears(base, Math.max(umbralEdad - 1, 0)), 'yyyy-MM-dd') : hoyISO()
  const maximaFechaMenor = hoyISO() < format(base, 'yyyy-MM-dd') ? hoyISO() : format(base, 'yyyy-MM-dd')

  const armar = (nuevosAdultos: string[], nuevosMenores: string[]) => alCambiar([...nuevosAdultos, ...nuevosMenores])
  const cambiarAdultos = (cantidad: number) => {
    if (cantidad < 1) return
    armar(
      cantidad > adultos.length ? [...adultos, ...Array(cantidad - adultos.length).fill('')] : adultos.slice(0, cantidad),
      menores,
    )
  }
  const cambiarMenor = (i: number, valor: string) => {
    if (!esFechaValida(valor)) return
    if (!esMenorNoPaga(valor, umbralEdad, referencia)) {
      setErrorMenor({ indice: i, texto: `Con esa fecha tendría ${umbralEdad} años o más: súmalo a los adultos.` })
      return
    }
    setErrorMenor(null)
    armar(adultos, menores.map((m, j) => (j === i ? valor : m)))
  }
  const quitarMenor = (i: number) => {
    setErrorMenor(null)
    armar(adultos, menores.filter((_, j) => j !== i))
  }

  const lista = (
    <div className="flex flex-col gap-4">
      {/* ---------- Adultos ---------- */}
      <div className="flex items-center justify-between gap-3">
        <div>
          <p id={`${id}-adultos`} className="text-sm font-medium">
            Adultos
          </p>
          <p className="text-xs text-muted-foreground">
            {umbralEdad !== null ? `Desde ${umbralEdad} años. Pagan noche.` : 'Pagan noche.'}
          </p>
        </div>
        <div className="flex items-center gap-2" role="group" aria-labelledby={`${id}-adultos`}>
          <Button
            type="button"
            variant="outline"
            size="icon"
            aria-label="Quitar un adulto"
            disabled={adultos.length <= 1}
            onClick={() => cambiarAdultos(adultos.length - 1)}
          >
            <Minus aria-hidden="true" />
          </Button>
          <span className="w-6 text-center text-base font-semibold tabular-nums" aria-live="polite">
            {adultos.length}
          </span>
          <Button
            type="button"
            variant="outline"
            size="icon"
            aria-label="Agregar un adulto"
            disabled={lleno}
            onClick={() => cambiarAdultos(adultos.length + 1)}
          >
            <Plus aria-hidden="true" />
          </Button>
        </div>
      </div>
      {adultos.length === 0 && (
        <p role="alert" className="text-xs font-medium text-error">
          {MENSAJE_SIN_ADULTOS}.
        </p>
      )}

      {/* ---------- Menores (solo si el alojamiento tiene umbral de edad) ---------- */}
      {umbralEdad !== null && (
        <div className="flex flex-col gap-3 border-t border-border pt-4">
          <div>
            <p className="text-sm font-medium">Menores de {umbralEdad} años</p>
            <p className="text-xs text-muted-foreground">Van con los adultos. No pagan noche, pero ocupan cupo.</p>
          </div>
          {menores.length > 0 && (
            <ul className="flex flex-col gap-3">
              {menores.map((fecha, i) => (
                <li key={i} className="flex flex-col gap-1.5">
                  <label htmlFor={`${id}-menor-${i}`} className="text-sm">
                    Menor {i + 1} <span className="text-muted-foreground">· fecha de nacimiento</span>
                  </label>
                  <div className="flex gap-2">
                    <Input
                      id={`${id}-menor-${i}`}
                      type="date"
                      min={nacidoDespuesDe ?? undefined}
                      max={maximaFechaMenor}
                      value={fecha}
                      aria-invalid={errorMenor?.indice === i || undefined}
                      onChange={(e) => cambiarMenor(i, e.target.value)}
                    />
                    <Button
                      type="button"
                      variant="ghost"
                      size="icon"
                      aria-label={`Quitar al menor ${i + 1}`}
                      onClick={() => quitarMenor(i)}
                    >
                      <Trash2 aria-hidden="true" />
                    </Button>
                  </div>
                  {errorMenor?.indice === i && <p className="text-xs font-medium text-error">{errorMenor.texto}</p>}
                </li>
              ))}
            </ul>
          )}
          <Button type="button" variant="outline" disabled={lleno} onClick={() => armar(adultos, [...menores, fechaPropuesta])}>
            <Plus aria-hidden="true" /> Agregar menor
          </Button>
        </div>
      )}
      {lleno && <p className="text-xs text-muted-foreground">Llegaste a la capacidad del apartamento ({maximo} personas).</p>}
    </div>
  )

  if (!desplegable) return <div className={className}>{lista}</div>

  return (
    <div ref={contenedor} className={cn('relative', className)}>
      <button
        type="button"
        aria-expanded={abierto}
        aria-controls={`${id}-panel`}
        onClick={() => setAbierto((a) => !a)}
        className="flex h-11 w-full items-center gap-2 rounded-campo border border-input bg-card px-3.5 text-left text-sm transition-colors outline-none focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 dark:bg-input/30"
      >
        <Users aria-hidden="true" className="size-4 shrink-0 text-coral" />
        <span className="flex-1 truncate">{resumenHuespedes(huespedes, umbralEdad, referencia)}</span>
        <ChevronDown aria-hidden="true" className={cn('size-4 transition-transform', abierto && 'rotate-180')} />
      </button>
      {abierto && (
        <div
          id={`${id}-panel`}
          className="absolute right-0 left-0 z-30 mt-2 rounded-tarjeta border border-border bg-popover p-4 shadow-tarjeta sm:left-auto sm:w-96"
        >
          {lista}
          <Button type="button" className="mt-3 w-full" onClick={() => setAbierto(false)}>
            Listo
          </Button>
        </div>
      )}
    </div>
  )
}

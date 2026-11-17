import { useState, type FormEvent } from 'react'
import { Search } from 'lucide-react'
import { z } from 'zod'
import { Campo } from '@/components/coral'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { cn } from '@/lib/utils'
import { esFechaISO, fecha, finDespuesDeInicio, useValidacion } from '@/lib/validacion'
import { grupoSinAdultos, MENSAJE_SIN_ADULTOS, type Estancia } from './estancia'
import { hoyISO, sumarDias } from './reglas'
import { SelectorHuespedes } from './SelectorHuespedes'

interface Props {
  inicial: Estancia
  umbralEdad: number | null
  alBuscar: (estancia: Estancia) => void
  textoBoton?: string
  className?: string
}

/**
 * Búsqueda de disponibles: llegada no pasada, salida después de la llegada, cada huésped con fecha de nacimiento
 * vacía o válida y no futura, y al menos un adulto que pague noche.
 */
function esquemaBusqueda(umbralEdad: number | null) {
  return z
    .object({
      entrada: fecha('Elige la fecha de llegada.').refine((f) => f >= hoyISO(), 'La llegada no puede ser en el pasado.'),
      salida: fecha('Elige la fecha de salida.'),
      huespedes: z
        .array(
          z
            .string()
            .refine((f) => f === '' || esFechaISO(f), 'Revisa las fechas de nacimiento de los huéspedes.')
            .refine((f) => f === '' || f <= hoyISO(), 'Ninguna fecha de nacimiento puede ser futura.'),
        )
        .min(1, 'Agrega al menos un huésped.'),
    })
    .superRefine(finDespuesDeInicio('entrada', 'salida', 'La salida debe ser después de la llegada.'))
    .superRefine((datos, contexto) => {
      if (datos.huespedes.length > 0 && grupoSinAdultos(datos.huespedes, umbralEdad, datos.entrada))
        contexto.addIssue({ code: 'custom', path: ['huespedes'], message: `${MENSAJE_SIN_ADULTOS}.` })
    })
}

/** Llegada, salida y huéspedes; valida que la salida sea posterior a la llegada */
export function BuscadorEstancia({ inicial, umbralEdad, alBuscar, textoBoton = 'Buscar disponibles', className }: Props) {
  const [entrada, setEntrada] = useState(inicial.entrada)
  const [salida, setSalida] = useState(inicial.salida)
  const [huespedes, setHuespedes] = useState(inicial.huespedes)
  const validacion = useValidacion(esquemaBusqueda(umbralEdad), { entrada, salida, huespedes })
  const errores = validacion.errores
  // El error de un huésped ("huespedes.1") se muestra junto al selector
  const errorHuespedes =
    errores.huespedes ?? Object.entries(errores).find(([clave]) => clave.startsWith('huespedes.'))?.[1]

  const enviar = (e: FormEvent) => {
    e.preventDefault()
    if (validacion.comprobar()) alBuscar({ entrada, salida, huespedes })
  }

  return (
    <form
      noValidate
      onSubmit={enviar}
      aria-label="Buscar apartamentos disponibles"
      className={cn(
        'grid gap-4 rounded-tarjeta border border-border bg-card p-4 shadow-tarjeta sm:grid-cols-2 md:p-5 lg:grid-cols-[1fr_1fr_1.3fr_auto] lg:items-start',
        className,
      )}
    >
      <Campo id="buscar-entrada" etiqueta="Llegada" error={errores.entrada}>
        <Input
          type="date"
          min={hoyISO()}
          value={entrada}
          onChange={(e) => {
            const valor = e.target.value
            setEntrada(valor)
            // Si la salida queda antes de la llegada, se propone una noche
            if (valor && (!salida || salida <= valor)) setSalida(sumarDias(valor, 1))
          }}
        />
      </Campo>
      <Campo id="buscar-salida" etiqueta="Salida" error={errores.salida}>
        <Input
          type="date"
          min={entrada ? sumarDias(entrada, 1) : hoyISO()}
          value={salida}
          onChange={(e) => setSalida(e.target.value)}
        />
      </Campo>
      <div className="flex flex-col gap-1.5 sm:col-span-2 lg:col-span-1">
        <span className="text-sm font-medium" id="buscar-huespedes">
          Huéspedes
        </span>
        <div role="group" aria-labelledby="buscar-huespedes">
          <SelectorHuespedes
            desplegable
            huespedes={huespedes}
            alCambiar={setHuespedes}
            umbralEdad={umbralEdad}
            referencia={entrada}
          />
        </div>
        {errorHuespedes && <p className="text-xs font-medium text-error">{errorHuespedes}</p>}
      </div>
      <Button type="submit" className="h-11 sm:col-span-2 lg:col-span-1 lg:mt-[1.625rem]">
        <Search aria-hidden="true" /> {textoBoton}
      </Button>
    </form>
  )
}

import { useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { CalendarX2, Eye, Loader2, MoreHorizontal, Pencil, Power, ScrollText, Wrench } from 'lucide-react'
import { toast } from 'sonner'
import { z } from 'zod'
import {
  useActivarApartamento,
  useLevantarBloqueo,
  usePonerFueraDeServicio,
  useRegistrarBloqueo,
  useRestablecerServicio,
  useRetirarApartamento,
} from '@/api/apartamentos'
import type { BloqueoResponse, EstadoOperativo } from '@/api/tipos'
import { AvisoError, Campo, ConfirmarDialogo, formatearFecha } from '@/components/coral'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { Input } from '@/components/ui/input'
import {
  erroresDelServidor,
  esErrorDeCampos,
  fecha,
  finDespuesDeInicio,
  MAXIMO_MOTIVO,
  textoObligatorio,
  unirErrores,
  useValidacion,
} from '@/lib/validacion'
import { claseTextarea } from '../calendario/estilos'
import { hoyISO, sumarDias } from '../calendario/fechas'
import { useEsAdministrador } from './utilidades'

export interface ApartamentoBasico {
  identificacion: string
  nombre: string
  estadoOperativo: EstadoOperativo
}

// ---------- Bloqueo ----------

// RegistrarBloqueoRequest: las dos fechas y el motivo; el día en que vuelve a la venta va después de la primera noche
const esquemaBloqueo = z
  .object({
    fechaInicio: fecha('Elige la primera noche'),
    fechaFin: fecha('Elige el día en que vuelve a la venta'),
    motivo: textoObligatorio('Escribe el motivo', MAXIMO_MOTIVO, 'El motivo admite máximo 250 caracteres'),
  })
  .superRefine(finDespuesDeInicio('fechaInicio', 'fechaFin', 'Debe ser posterior a la primera noche'))

interface DialogoProps {
  apartamento: ApartamentoBasico
  abierto: boolean
  alCambiar: (abierto: boolean) => void
}

/** Registra un bloqueo: retira el apartamento de la venta en un rango de noches */
export function DialogoBloqueo({ apartamento, abierto, alCambiar }: DialogoProps) {
  const bloqueo = useRegistrarBloqueo(apartamento.identificacion)
  const [fechaInicio, setFechaInicio] = useState(() => hoyISO())
  const [fechaFin, setFechaFin] = useState(() => sumarDias(hoyISO(), 1))
  const [motivo, setMotivo] = useState('')
  const validacion = useValidacion(esquemaBloqueo, { fechaInicio, fechaFin, motivo })
  const errores = unirErrores(validacion.errores, erroresDelServidor(bloqueo.error))

  const cambiar = (valor: boolean) => {
    if (bloqueo.isPending) return
    if (!valor) {
      bloqueo.reset()
      validacion.reiniciar()
      setMotivo('')
    }
    alCambiar(valor)
  }

  const enviar = (evento: FormEvent) => {
    evento.preventDefault()
    const limpios = validacion.comprobar()
    if (!limpios) return

    bloqueo.mutate(limpios, {
      onSuccess: () => {
        toast.success('Bloqueo registrado', { description: apartamento.identificacion })
        cambiar(false)
      },
    })
  }

  const errorGeneral = bloqueo.error && !esErrorDeCampos(bloqueo.error) ? bloqueo.error : null

  return (
    <Dialog open={abierto} onOpenChange={cambiar}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle className="font-titulo text-xl">Registrar bloqueo</DialogTitle>
          <DialogDescription>
            {apartamento.identificacion} · {apartamento.nombre} no se podrá reservar en esas noches. Se rechaza si ya
            hay reservas activas en el rango.
          </DialogDescription>
        </DialogHeader>
        <form id="form-bloqueo" noValidate onSubmit={enviar} className="flex flex-col gap-4">
          <div className="grid gap-4 sm:grid-cols-2">
            <Campo id="bloqueo-inicio" etiqueta="Primera noche" error={errores.fechaInicio}>
              <Input type="date" value={fechaInicio} onChange={(e) => setFechaInicio(e.target.value)} />
            </Campo>
            <Campo
              id="bloqueo-fin"
              etiqueta="Vuelve a la venta"
              ayuda="Primera noche que ya no queda bloqueada"
              error={errores.fechaFin}
            >
              <Input type="date" value={fechaFin} min={fechaInicio} onChange={(e) => setFechaFin(e.target.value)} />
            </Campo>
          </div>
          <Campo id="bloqueo-motivo" etiqueta="Motivo" error={errores.motivo}>
            <textarea
              value={motivo}
              onChange={(e) => setMotivo(e.target.value)}
              className={claseTextarea}
              placeholder="Mantenimiento del aire acondicionado"
            />
          </Campo>
          {errorGeneral && <AvisoError error={errorGeneral} />}
        </form>
        <DialogFooter>
          <Button variant="outline" onClick={() => cambiar(false)} disabled={bloqueo.isPending}>
            Cancelar
          </Button>
          <Button type="submit" form="form-bloqueo" disabled={bloqueo.isPending}>
            {bloqueo.isPending && <Loader2 aria-hidden="true" className="animate-spin" />}
            Registrar bloqueo
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

// ---------- Fuera de servicio y restablecer ----------

/** Pone fuera de servicio o restablece, según el estado actual */
export function DialogoServicio({ apartamento, abierto, alCambiar }: DialogoProps) {
  const fuera = usePonerFueraDeServicio()
  const restablecer = useRestablecerServicio()
  const estaFuera = apartamento.estadoOperativo === 'FUERA_DE_SERVICIO'
  const accion = estaFuera ? restablecer : fuera

  const cambiar = (valor: boolean) => {
    if (accion.isPending) return
    if (!valor) accion.reset()
    alCambiar(valor)
  }

  return (
    <ConfirmarDialogo
      abierto={abierto}
      alCambiar={cambiar}
      titulo={estaFuera ? 'Restablecer el servicio' : 'Poner fuera de servicio'}
      descripcion={
        estaFuera
          ? `${apartamento.identificacion} vuelve al servicio y queda pendiente de preparación.`
          : `${apartamento.identificacion} quedará fuera de servicio. Esto no bloquea la venta futura: para eso registra un bloqueo.`
      }
      textoConfirmar={estaFuera ? 'Restablecer' : 'Poner fuera de servicio'}
      peligroso={!estaFuera}
      cargando={accion.isPending}
      alConfirmar={() =>
        accion.mutate(apartamento.identificacion, {
          onSuccess: () => {
            toast.success(estaFuera ? 'Servicio restablecido' : 'Apartamento fuera de servicio', {
              description: apartamento.identificacion,
            })
            cambiar(false)
          },
        })
      }
    >
      {accion.error && <AvisoError error={accion.error} />}
    </ConfirmarDialogo>
  )
}

// ---------- Activar ----------

export function DialogoActivar({ apartamento, abierto, alCambiar }: DialogoProps) {
  const activar = useActivarApartamento()

  const cambiar = (valor: boolean) => {
    if (activar.isPending) return
    if (!valor) activar.reset()
    alCambiar(valor)
  }

  return (
    <ConfirmarDialogo
      abierto={abierto}
      alCambiar={cambiar}
      titulo="Activar apartamento"
      descripcion={`${apartamento.identificacion} saldrá a la venta y aparecerá en la búsqueda. Necesita tarifa en todas las temporadas.`}
      textoConfirmar="Activar"
      cargando={activar.isPending}
      alConfirmar={() =>
        activar.mutate(apartamento.identificacion, {
          onSuccess: () => {
            toast.success('Apartamento activo', { description: apartamento.identificacion })
            cambiar(false)
          },
        })
      }
    >
      {activar.error && <AvisoError titulo="No se pudo activar" error={activar.error} />}
    </ConfirmarDialogo>
  )
}

// ---------- Retirar ----------

export function DialogoRetirar({
  apartamento,
  abierto,
  alCambiar,
  alRetirar,
}: DialogoProps & { alRetirar?: () => void }) {
  const retirar = useRetirarApartamento()

  const cambiar = (valor: boolean) => {
    if (retirar.isPending) return
    if (!valor) retirar.reset()
    alCambiar(valor)
  }

  return (
    <ConfirmarDialogo
      abierto={abierto}
      alCambiar={cambiar}
      titulo="Retirar de la venta"
      descripcion={
        <>
          Es una <strong>eliminación lógica</strong>: {apartamento.identificacion} · {apartamento.nombre} deja de salir
          en el listado y en la búsqueda, pero no se borra. Las reservas pasadas y el historial de novedades lo siguen
          mostrando. No se puede retirar si tiene reservas activas o futuras, y después no se puede volver a activar.
        </>
      }
      textoConfirmar="Retirar"
      peligroso
      cargando={retirar.isPending}
      alConfirmar={() =>
        retirar.mutate(apartamento.identificacion, {
          onSuccess: () => {
            toast.success('Apartamento retirado de la venta', { description: apartamento.identificacion })
            cambiar(false)
            alRetirar?.()
          },
        })
      }
    >
      {retirar.error && <AvisoError error={retirar.error} />}
    </ConfirmarDialogo>
  )
}

// ---------- Levantar un bloqueo ----------

/** Confirma y levanta un bloqueo vigente: sus noches vuelven a la venta y el bloqueo queda en el historial */
export function DialogoLevantarBloqueo({
  apartamento,
  bloqueo,
  alCambiar,
}: {
  apartamento: ApartamentoBasico
  bloqueo: BloqueoResponse | null
  alCambiar: (abierto: boolean) => void
}) {
  const levantar = useLevantarBloqueo(apartamento.identificacion)

  const cambiar = (valor: boolean) => {
    if (levantar.isPending) return
    if (!valor) levantar.reset()
    alCambiar(valor)
  }

  return (
    <ConfirmarDialogo
      abierto={bloqueo !== null}
      alCambiar={cambiar}
      titulo="Levantar bloqueo"
      descripcion={
        bloqueo && (
          <>
            Las noches de {apartamento.identificacion} desde el {formatearFecha(bloqueo.fechaInicio, "d 'de' MMMM")}{' '}
            hasta el {formatearFecha(sumarDias(bloqueo.fechaFin, -1), "d 'de' MMMM")} vuelven a la venta. El bloqueo
            («{bloqueo.motivo}») no se borra: queda registrado como levantado hoy.
          </>
        )
      }
      textoConfirmar="Levantar"
      cargando={levantar.isPending}
      alConfirmar={() =>
        bloqueo &&
        levantar.mutate(bloqueo.id, {
          onSuccess: () => {
            toast.success('Bloqueo levantado', { description: `${apartamento.identificacion} · ${bloqueo.motivo}` })
            cambiar(false)
          },
        })
      }
    >
      {levantar.error && <AvisoError error={levantar.error} />}
    </ConfirmarDialogo>
  )
}

// ---------- Menú de acciones de una fila ----------

type Dialogo = 'bloqueo' | 'servicio' | null

/** Menú "…" de una fila de apartamento; las acciones de cambio son solo del administrador */
export function MenuAccionesApartamento({ apartamento }: { apartamento: ApartamentoBasico }) {
  const esAdmin = useEsAdministrador()
  const [dialogo, setDialogo] = useState<Dialogo>(null)
  const id = encodeURIComponent(apartamento.identificacion)
  const estaFuera = apartamento.estadoOperativo === 'FUERA_DE_SERVICIO'

  return (
    <>
      <DropdownMenu>
        <DropdownMenuTrigger asChild>
          <Button variant="ghost" size="icon" aria-label={`Acciones de ${apartamento.identificacion}`}>
            <MoreHorizontal aria-hidden="true" />
          </Button>
        </DropdownMenuTrigger>
        <DropdownMenuContent align="end" className="min-w-52">
          <DropdownMenuItem asChild>
            <Link to={`/panel/apartamentos/${id}`}>
              <Eye aria-hidden="true" /> Ver detalle
            </Link>
          </DropdownMenuItem>
          {esAdmin && (
            <>
              <DropdownMenuItem asChild>
                <Link to={`/panel/apartamentos/${id}/editar`}>
                  <Pencil aria-hidden="true" /> Editar
                </Link>
              </DropdownMenuItem>
              <DropdownMenuItem asChild>
                <Link to={`/panel/apartamentos/${id}/novedades`}>
                  <ScrollText aria-hidden="true" /> Novedades
                </Link>
              </DropdownMenuItem>
              <DropdownMenuSeparator />
              <DropdownMenuItem onSelect={() => setDialogo('bloqueo')}>
                <CalendarX2 aria-hidden="true" /> Registrar bloqueo
              </DropdownMenuItem>
              <DropdownMenuItem
                disabled={apartamento.estadoOperativo === 'OCUPADO'}
                onSelect={() => setDialogo('servicio')}
              >
                {estaFuera ? <Power aria-hidden="true" /> : <Wrench aria-hidden="true" />}
                {estaFuera ? 'Restablecer servicio' : 'Poner fuera de servicio'}
              </DropdownMenuItem>
            </>
          )}
        </DropdownMenuContent>
      </DropdownMenu>

      {esAdmin && (
        <>
          <DialogoBloqueo
            apartamento={apartamento}
            abierto={dialogo === 'bloqueo'}
            alCambiar={(abierto) => setDialogo(abierto ? 'bloqueo' : null)}
          />
          <DialogoServicio
            apartamento={apartamento}
            abierto={dialogo === 'servicio'}
            alCambiar={(abierto) => setDialogo(abierto ? 'servicio' : null)}
          />
        </>
      )}
    </>
  )
}

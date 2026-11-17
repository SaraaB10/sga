import { useState, type FormEvent } from 'react'
import { Loader2, Lock, Pencil, Plus, Power, PowerOff } from 'lucide-react'
import { toast } from 'sonner'
import { z } from 'zod'
import {
  useActualizarServicioAdicional,
  useCambiarEstadoServicioAdicional,
  useCrearServicioAdicional,
  type ServicioAdicionalRequest,
} from '@/api/configuracion'
import type { ServicioAdicionalResponse } from '@/api/tipos'
import { AvisoError, Campo, Dinero } from '@/components/coral'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import {
  aPesos,
  erroresDelServidor,
  esErrorDeCampos,
  pesos,
  textoObligatorio,
  textoOpcional,
  unirErrores,
  useValidacion,
} from '@/lib/validacion'
import { claseTextarea } from '@/pages/publico/comun/estilos'

/**
 * Servicios adicionales del alojamiento: crear, editar (nombre, descripción, si genera cargo y valor), desactivar y
 * volver a activar. Si un servicio tiene lógica propia (por ejemplo, la recepción nocturna que se cobra sola a las
 * llegadas tarde), la pantalla explica por qué no se puede desactivar ni dejar sin cargo.
 */
export function ServiciosAdicionales({ servicios }: { servicios: ServicioAdicionalResponse[] }) {
  // null: cerrado · 'nuevo': crear · un servicio: editarlo
  const [editando, setEditando] = useState<ServicioAdicionalResponse | 'nuevo' | null>(null)
  const cambiarEstado = useCambiarEstadoServicioAdicional()

  const alternar = (s: ServicioAdicionalResponse) =>
    cambiarEstado.mutate(
      { id: s.id, activar: !s.activo },
      { onSuccess: () => toast.success(s.activo ? 'Servicio desactivado' : 'Servicio activado', { description: s.nombre }) },
    )

  return (
    <div className="flex flex-col gap-3 rounded-tarjeta border border-border bg-card p-5 shadow-tarjeta md:p-6">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="flex flex-col gap-1">
          <h2 className="font-titulo text-xl font-semibold">Servicios adicionales</h2>
          <p className="text-xs text-muted-foreground">
            Se cargan al folio desde recepción. Un servicio desactivado deja de ofrecerse; sus cargos anteriores se
            conservan.
          </p>
        </div>
        <Button size="sm" onClick={() => setEditando('nuevo')}>
          <Plus aria-hidden="true" /> Nuevo servicio
        </Button>
      </div>

      {cambiarEstado.error && <AvisoError titulo="No se pudo cambiar el servicio" error={cambiarEstado.error} />}

      {servicios.length === 0 ? (
        <p className="text-sm text-muted-foreground">El alojamiento no tiene servicios adicionales.</p>
      ) : (
        <ul className="flex flex-col divide-y divide-border">
          {servicios.map((s) => {
            const ocupado = cambiarEstado.isPending && cambiarEstado.variables?.id === s.id
            return (
              <li key={s.id} className="flex flex-col gap-2 py-3 text-sm sm:flex-row sm:items-start sm:justify-between">
                <div className="min-w-0">
                  <p className="font-medium">
                    {s.nombre}
                    {!s.activo && <span className="ml-2 text-xs font-normal text-muted-foreground">(inactivo)</span>}
                  </p>
                  <p className="font-mono text-xs text-muted-foreground">{s.id}</p>
                  {s.descripcion && <p className="text-muted-foreground">{s.descripcion}</p>}
                  {s.motivoObligatorio && (
                    <p className="mt-1 inline-flex items-start gap-1.5 text-xs text-muted-foreground">
                      <Lock aria-hidden="true" className="mt-0.5 size-3.5 shrink-0" />
                      {s.motivoObligatorio}
                    </p>
                  )}
                </div>
                <div className="flex shrink-0 flex-wrap items-center gap-2 sm:justify-end">
                  <span className="mr-1">
                    {s.generaCargo ? (
                      <Dinero valor={s.valor} className="font-semibold" />
                    ) : (
                      <span className="text-muted-foreground">Sin cargo</span>
                    )}
                  </span>
                  <Button size="sm" variant="outline" onClick={() => setEditando(s)}>
                    <Pencil aria-hidden="true" /> Editar
                  </Button>
                  {!s.motivoObligatorio && (
                    <Button size="sm" variant="ghost" disabled={ocupado} onClick={() => alternar(s)}>
                      {ocupado ? (
                        <Loader2 aria-hidden="true" className="animate-spin" />
                      ) : s.activo ? (
                        <PowerOff aria-hidden="true" />
                      ) : (
                        <Power aria-hidden="true" />
                      )}
                      {s.activo ? 'Desactivar' : 'Activar'}
                    </Button>
                  )}
                </div>
              </li>
            )
          })}
        </ul>
      )}

      <DialogoServicio
        key={editando === null ? 'cerrado' : editando === 'nuevo' ? 'nuevo' : editando.id}
        servicio={editando}
        alCerrar={() => setEditando(null)}
      />
    </div>
  )
}

interface Formulario {
  id: string
  nombre: string
  descripcion: string
  generaCargo: boolean
  valor: string
}

/*
 * ActualizarServicioAdicionalRequest: nombre de hasta 100 caracteres, descripción de hasta 300 y, si genera cargo,
 * un valor mayor que cero. Al crear (RegistrarServicioAdicionalRequest) se suma el id, de hasta 40 caracteres.
 */
const esquemaEdicion = z
  .object({
    id: z.string(),
    nombre: textoObligatorio('Escribe el nombre', 100, 'El nombre admite máximo 100 caracteres'),
    descripcion: textoOpcional(300, 'La descripción admite máximo 300 caracteres'),
    generaCargo: z.boolean(),
    valor: z.string(),
  })
  .superRefine((datos, contexto) => {
    if (datos.generaCargo && !pesos().safeParse(datos.valor).success)
      contexto.addIssue({
        code: 'custom',
        path: ['valor'],
        message: 'Un servicio con cargo necesita un valor mayor que cero, sin decimales',
      })
  })

const esquemaNuevo = esquemaEdicion.safeExtend({
  id: textoObligatorio('Escribe la identificación, por ejemplo SRV-SPA', 40, 'La identificación admite máximo 40 caracteres').regex(
    /^[A-Za-z0-9][A-Za-z0-9-]*$/,
    'Usa letras, números y guiones, por ejemplo SRV-SPA',
  ),
})

function DialogoServicio({
  servicio,
  alCerrar,
}: {
  servicio: ServicioAdicionalResponse | 'nuevo' | null
  alCerrar: () => void
}) {
  const nuevo = servicio === 'nuevo'
  const existente = servicio !== null && servicio !== 'nuevo' ? servicio : null
  const crear = useCrearServicioAdicional()
  const actualizar = useActualizarServicioAdicional()
  const guardando = crear.isPending || actualizar.isPending
  const errorServidor = crear.error ?? actualizar.error
  const [datos, setDatos] = useState<Formulario>({
    id: '',
    nombre: existente?.nombre ?? '',
    descripcion: existente?.descripcion ?? '',
    generaCargo: existente?.generaCargo ?? true,
    valor: existente?.generaCargo ? String(existente.valor) : '',
  })
  const validacion = useValidacion(nuevo ? esquemaNuevo : esquemaEdicion, datos)
  const errores = unirErrores(validacion.errores, erroresDelServidor(errorServidor))
  const error = (clave: keyof Formulario) => errores[clave]

  const enviar = (evento: FormEvent) => {
    evento.preventDefault()
    const limpios = validacion.comprobar()
    if (!limpios) return

    const solicitud: ServicioAdicionalRequest = {
      nombre: limpios.nombre,
      descripcion: limpios.descripcion === '' ? null : limpios.descripcion,
      generaCargo: datos.generaCargo,
      valor: datos.generaCargo ? aPesos(datos.valor) : null,
    }
    const listo = (texto: string) => () => {
      toast.success(texto, { description: solicitud.nombre })
      alCerrar()
    }
    if (nuevo) crear.mutate({ id: datos.id.trim().toUpperCase(), ...solicitud }, { onSuccess: listo('Servicio creado') })
    else if (existente)
      actualizar.mutate({ id: existente.id, datos: solicitud }, { onSuccess: listo('Servicio actualizado') })
  }

  return (
    <Dialog open={servicio !== null} onOpenChange={(abierto) => !abierto && !guardando && alCerrar()}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle className="font-titulo text-xl">{nuevo ? 'Nuevo servicio' : 'Editar servicio'}</DialogTitle>
          <DialogDescription>
            {nuevo
              ? 'Queda activo y recepción lo puede cargar al folio de inmediato.'
              : 'El cambio rige para los cargos nuevos; los que ya están en los folios no cambian.'}
          </DialogDescription>
        </DialogHeader>
        <form id="form-servicio" noValidate onSubmit={enviar} className="flex flex-col gap-4">
          {nuevo ? (
            <Campo id="servicio-id" etiqueta="Identificación" ayuda="No se puede cambiar después." error={error('id')}>
              <Input
                placeholder="SRV-SPA"
                value={datos.id}
                onChange={(e) => setDatos((d) => ({ ...d, id: e.target.value }))}
              />
            </Campo>
          ) : (
            <p className="font-mono text-xs text-muted-foreground">{existente?.id}</p>
          )}
          <Campo id="servicio-nombre" etiqueta="Nombre" error={error('nombre')}>
            <Input value={datos.nombre} onChange={(e) => setDatos((d) => ({ ...d, nombre: e.target.value }))} />
          </Campo>
          <Campo id="servicio-descripcion" etiqueta="Descripción (opcional)" error={error('descripcion')}>
            <textarea
              className={claseTextarea}
              value={datos.descripcion}
              onChange={(e) => setDatos((d) => ({ ...d, descripcion: e.target.value }))}
            />
          </Campo>
          <div className="flex flex-col gap-1.5">
            <label className="flex items-center gap-2 text-sm font-medium">
              <input
                type="checkbox"
                className="size-4 accent-coral"
                checked={datos.generaCargo}
                disabled={!!existente?.motivoObligatorio}
                onChange={(e) => setDatos((d) => ({ ...d, generaCargo: e.target.checked }))}
              />
              Genera cargo en el folio
            </label>
            {existente?.motivoObligatorio && (
              <p className="inline-flex items-start gap-1.5 text-xs text-muted-foreground">
                <Lock aria-hidden="true" className="mt-0.5 size-3.5 shrink-0" />
                {existente.motivoObligatorio}
              </p>
            )}
          </div>
          {datos.generaCargo && (
            <Campo id="servicio-valor" etiqueta="Valor en pesos" error={error('valor')}>
              <Input
                inputMode="numeric"
                placeholder="180000"
                value={datos.valor}
                onChange={(e) => setDatos((d) => ({ ...d, valor: e.target.value }))}
              />
            </Campo>
          )}
          {errorServidor && !esErrorDeCampos(errorServidor) && <AvisoError error={errorServidor} />}
        </form>
        <DialogFooter>
          <Button variant="outline" onClick={alCerrar} disabled={guardando}>
            Cancelar
          </Button>
          <Button type="submit" form="form-servicio" disabled={guardando}>
            {guardando && <Loader2 aria-hidden="true" className="animate-spin" />}
            {nuevo ? 'Crear servicio' : 'Guardar cambios'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

import { useState, type FormEvent } from 'react'
import { CreditCard, Loader2, Plus } from 'lucide-react'
import { toast } from 'sonner'
import { z } from 'zod'
import {
  useActivarMedioPago,
  useCrearMedioPago,
  useDesactivarMedioPago,
  useTodosLosMediosPago,
} from '@/api/configuracion'
import { AvisoError, Campo } from '@/components/coral'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Skeleton } from '@/components/ui/skeleton'
import { erroresDelServidor, esErrorDeCampos, textoObligatorio, unirErrores, useValidacion } from '@/lib/validacion'

/*
 * Medios de pago aceptados (L-18): el administrador agrega medios y los activa o desactiva. Solo los activos se
 * ofrecen al registrar un pago y se muestran al huésped en el detalle del apartamento. Desactivar no borra: los pagos
 * ya registrados conservan su medio.
 */

// CrearMedioPagoRequest: de 2 a 80 caracteres, con al menos dos letras o números para formar el código
const esquema = z.object({
  nombre: textoObligatorio('Escribe el nombre del medio de pago', 80, 'El nombre admite máximo 80 caracteres').refine(
    (v) => (v.match(/[\p{L}\p{N}]/gu) ?? []).length >= 2,
    'El nombre del medio de pago debe tener al menos dos letras o números',
  ),
})

export function MediosPago() {
  const medios = useTodosLosMediosPago()
  const crear = useCrearMedioPago()
  const activar = useActivarMedioPago()
  const desactivar = useDesactivarMedioPago()
  const [nombre, setNombre] = useState('')
  const validacion = useValidacion(esquema, { nombre })
  const errores = unirErrores(validacion.errores, erroresDelServidor(crear.error))
  const cambio = activar.error ?? desactivar.error

  const agregar = (evento: FormEvent) => {
    evento.preventDefault()
    const limpios = validacion.comprobar()
    if (!limpios) return
    crear.mutate(limpios.nombre, {
      onSuccess: (medio) => {
        setNombre('')
        validacion.reiniciar()
        toast.success('Medio de pago agregado', { description: medio.nombre })
      },
    })
  }

  const alternar = (codigo: string, activo: boolean, nombreMedio: string) => {
    activar.reset()
    desactivar.reset()
    const accion = activo ? desactivar : activar
    accion.mutate(codigo, {
      onSuccess: () => toast.success(activo ? 'Medio de pago desactivado' : 'Medio de pago activado', { description: nombreMedio }),
    })
  }

  return (
    <section
      aria-labelledby="titulo-medios"
      className="flex flex-col gap-4 rounded-tarjeta border border-border bg-card p-5 shadow-tarjeta md:p-6"
    >
      <div className="flex flex-col gap-1">
        <h2 id="titulo-medios" className="font-titulo text-xl font-semibold">
          Medios de pago
        </h2>
        <p className="text-sm text-muted-foreground">
          Los activos se ofrecen al registrar un pago y los ve el huésped en el detalle del apartamento.
        </p>
      </div>

      {medios.error ? (
        <AvisoError titulo="No se pudieron consultar los medios de pago" error={medios.error} />
      ) : !medios.data ? (
        <Skeleton className="h-28" />
      ) : (
        <ul className="flex flex-col divide-y divide-border">
          {medios.data.map((m) => (
            <li key={m.codigo} className="flex flex-wrap items-center justify-between gap-3 py-3">
              <div className="flex min-w-0 items-center gap-3">
                <CreditCard aria-hidden="true" className="size-5 shrink-0 text-muted-foreground" />
                <div className="min-w-0">
                  <p className="font-medium">{m.nombre}</p>
                  <p className="font-mono text-xs text-muted-foreground">{m.codigo}</p>
                </div>
                <span
                  className="rounded-full px-2.5 py-0.5 text-xs font-semibold"
                  style={m.activo ? { backgroundColor: '#DDF2EA', color: '#1C5E45' } : { backgroundColor: '#EFE7EC', color: '#57434F' }}
                >
                  {m.activo ? 'Activo' : 'Inactivo'}
                </span>
              </div>
              <Button
                type="button"
                variant={m.activo ? 'ghost' : 'outline'}
                size="sm"
                className={m.activo ? 'text-error' : undefined}
                disabled={activar.isPending || desactivar.isPending}
                onClick={() => alternar(m.codigo, m.activo, m.nombre)}
              >
                {m.activo ? 'Desactivar' : 'Activar'}
              </Button>
            </li>
          ))}
        </ul>
      )}
      {cambio && <AvisoError titulo="No se pudo cambiar el medio de pago" error={cambio} />}

      <form noValidate onSubmit={agregar} className="flex flex-col gap-3 sm:flex-row sm:items-start">
        <Campo id="medio-nombre" etiqueta="Nuevo medio de pago" error={errores.nombre} className="flex-1">
          <Input maxLength={80} placeholder="Por ejemplo, Efectivo" value={nombre} onChange={(e) => setNombre(e.target.value)} />
        </Campo>
        <Button type="submit" className="sm:mt-7" disabled={crear.isPending}>
          {crear.isPending ? <Loader2 aria-hidden="true" className="animate-spin" /> : <Plus aria-hidden="true" />}
          Agregar
        </Button>
      </form>
      {crear.error && !esErrorDeCampos(crear.error) && <AvisoError titulo="No se pudo agregar el medio de pago" error={crear.error} />}
    </section>
  )
}

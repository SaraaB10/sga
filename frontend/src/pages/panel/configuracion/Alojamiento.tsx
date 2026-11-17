import { useState, type FormEvent, type ReactNode } from 'react'
import { Loader2, Save } from 'lucide-react'
import { toast } from 'sonner'
import { z } from 'zod'
import { useActualizarAlojamiento, type ActualizarAlojamientoRequest } from '@/api/configuracion'
import { useAlojamiento } from '@/api/publico'
import type { AlojamientoResponse } from '@/api/tipos'
import { AvisoError, Campo } from '@/components/coral'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Skeleton } from '@/components/ui/skeleton'
import {
  decimal,
  erroresDelServidor,
  esErrorDeCampos,
  MAXIMO_DESCRIPCION,
  textoObligatorio,
  textoOpcional,
  unirErrores,
  useValidacion,
} from '@/lib/validacion'
import { claseTextarea } from '@/pages/publico/comun/estilos'
import { MediosPago } from './MediosPago'
import { ParametrosAlojamiento } from './ParametrosAlojamiento'
import { ServiciosAdicionales } from './ServiciosAdicionales'

interface Formulario {
  nombre: string
  descripcion: string
  ciudad: string
  direccion: string
  latitud: string
  longitud: string
  horaEntrada: string
  horaSalida: string
  normaConvivencia: string
}

const aFormulario = (a: AlojamientoResponse): Formulario => ({
  nombre: a.nombre ?? '',
  descripcion: a.descripcion ?? '',
  ciudad: a.ciudad ?? '',
  direccion: a.direccion ?? '',
  latitud: String(a.latitud ?? ''),
  longitud: String(a.longitud ?? ''),
  horaEntrada: a.horaEntrada?.slice(0, 5) ?? '',
  horaSalida: a.horaSalida?.slice(0, 5) ?? '',
  normaConvivencia: a.normaConvivencia ?? '',
})

const vacioANull = (texto: string) => (texto.trim() === '' ? null : texto.trim())

const HORA = /^\d{2}:\d{2}$/

// ActualizarAlojamientoRequest: nombre, ciudad, latitud y longitud obligatorios; el horario lleva las dos horas o ninguna
const esquema = z
  .object({
    nombre: textoObligatorio('Escribe el nombre', 100, 'El nombre admite máximo 100 caracteres'),
    descripcion: textoOpcional(MAXIMO_DESCRIPCION, 'La descripción admite máximo 1000 caracteres'),
    ciudad: textoObligatorio('Escribe la ciudad', 80, 'La ciudad admite máximo 80 caracteres'),
    direccion: textoOpcional(200, 'La dirección admite máximo 200 caracteres'),
    latitud: decimal('Escribe una latitud entre -90 y 90', -90, 90),
    longitud: decimal('Escribe una longitud entre -180 y 180', -180, 180),
    horaEntrada: z.string().refine((h) => h === '' || HORA.test(h), 'Escribe una hora válida'),
    horaSalida: z.string().refine((h) => h === '' || HORA.test(h), 'Escribe una hora válida'),
    normaConvivencia: textoOpcional(MAXIMO_DESCRIPCION, 'Las normas de convivencia admiten máximo 1000 caracteres'),
  })
  .superRefine((datos, contexto) => {
    if (!!datos.horaEntrada !== !!datos.horaSalida) {
      contexto.addIssue({
        code: 'custom',
        path: [datos.horaEntrada ? 'horaSalida' : 'horaEntrada'],
        message: 'Escribe las dos horas del horario o ninguna',
      })
    }
  })

export default function Alojamiento() {
  const alojamiento = useAlojamiento()

  return (
    <section className="mx-auto flex w-full max-w-5xl flex-col gap-6">
      <header>
        <h1 className="font-titulo text-3xl font-semibold">Alojamiento</h1>
        <p className="mt-1 text-sm text-muted-foreground">Datos, horario, normas y servicios que ven los huéspedes, y los parámetros de la operación.</p>
      </header>
      {alojamiento.error ? (
        <AvisoError titulo="No se pudo consultar el alojamiento" error={alojamiento.error} />
      ) : !alojamiento.data ? (
        <>
          <Skeleton className="h-96" />
          <Skeleton className="h-48" />
        </>
      ) : (
        <>
          <FormularioAlojamiento alojamiento={alojamiento.data} />
          <ServiciosAdicionales servicios={alojamiento.data.serviciosAdicionales} />
          <ParametrosAlojamiento parametros={alojamiento.data.parametros} />
          <MediosPago />
        </>
      )}
    </section>
  )
}

function FormularioAlojamiento({ alojamiento }: { alojamiento: AlojamientoResponse }) {
  const actualizar = useActualizarAlojamiento()
  const [datos, setDatos] = useState<Formulario>(() => aFormulario(alojamiento))
  const validacion = useValidacion(esquema, datos)
  const errores = unirErrores(validacion.errores, erroresDelServidor(actualizar.error))

  const campo = (clave: keyof Formulario) => ({
    value: datos[clave],
    onChange: (e: { target: { value: string } }) => setDatos((d) => ({ ...d, [clave]: e.target.value })),
  })
  const error = (clave: keyof Formulario) => errores[clave]

  const enviar = (evento: FormEvent) => {
    evento.preventDefault()
    const limpios = validacion.comprobar()
    if (!limpios) return

    const solicitud: ActualizarAlojamientoRequest = {
      nombre: limpios.nombre,
      descripcion: vacioANull(datos.descripcion),
      ciudad: limpios.ciudad,
      direccion: vacioANull(datos.direccion),
      latitud: Number(limpios.latitud.replace(',', '.')),
      longitud: Number(limpios.longitud.replace(',', '.')),
      normaConvivencia: vacioANull(datos.normaConvivencia),
      horaEntrada: datos.horaEntrada || null,
      horaSalida: datos.horaSalida || null,
    }
    actualizar.mutate(solicitud, {
      onSuccess: (nuevo) => {
        setDatos(aFormulario(nuevo))
        toast.success('Alojamiento actualizado')
      },
    })
  }

  return (
    <form
      noValidate
      onSubmit={enviar}
      className="flex flex-col gap-6 rounded-tarjeta border border-border bg-card p-5 shadow-tarjeta md:p-6"
    >
      <Seccion titulo="Datos">
        <div className="grid gap-4 sm:grid-cols-2">
          <Campo id="aloj-nombre" etiqueta="Nombre" error={error('nombre')}>
            <Input maxLength={100} {...campo('nombre')} />
          </Campo>
          <Campo id="aloj-ciudad" etiqueta="Ciudad" error={error('ciudad')}>
            <Input maxLength={80} {...campo('ciudad')} />
          </Campo>
          <Campo id="aloj-direccion" etiqueta="Dirección" error={error('direccion')} className="sm:col-span-2">
            <Input maxLength={200} {...campo('direccion')} />
          </Campo>
          <Campo id="aloj-latitud" etiqueta="Latitud" error={error('latitud')}>
            <Input inputMode="decimal" {...campo('latitud')} />
          </Campo>
          <Campo id="aloj-longitud" etiqueta="Longitud" error={error('longitud')}>
            <Input inputMode="decimal" {...campo('longitud')} />
          </Campo>
          <Campo id="aloj-descripcion" etiqueta="Descripción" error={error('descripcion')} className="sm:col-span-2">
            <textarea className={claseTextarea} maxLength={MAXIMO_DESCRIPCION} {...campo('descripcion')} />
          </Campo>
        </div>
      </Seccion>

      <Seccion titulo="Horario">
        <div className="grid grid-cols-2 gap-4">
          <Campo id="aloj-entrada" etiqueta="Hora de entrada" error={error('horaEntrada')}>
            <Input type="time" {...campo('horaEntrada')} />
          </Campo>
          <Campo id="aloj-salida" etiqueta="Hora de salida" error={error('horaSalida')}>
            <Input type="time" {...campo('horaSalida')} />
          </Campo>
        </div>
      </Seccion>

      <Seccion titulo="Normas de convivencia">
        <Campo id="aloj-normas" etiqueta="Normas" error={error('normaConvivencia')}>
          <textarea
            className={`${claseTextarea} min-h-32`}
            maxLength={MAXIMO_DESCRIPCION}
            {...campo('normaConvivencia')}
          />
        </Campo>
      </Seccion>

      {actualizar.error && !esErrorDeCampos(actualizar.error) && (
        <AvisoError titulo="No se pudo guardar" error={actualizar.error} />
      )}
      <div className="flex justify-end">
        <Button type="submit" disabled={actualizar.isPending} className="w-full sm:w-auto">
          {actualizar.isPending ? <Loader2 aria-hidden="true" className="animate-spin" /> : <Save aria-hidden="true" />}
          Guardar cambios
        </Button>
      </div>
    </form>
  )
}

function Seccion({ titulo, children }: { titulo: string; children: ReactNode }) {
  return (
    <fieldset className="flex flex-col gap-3">
      <legend className="mb-3 font-titulo text-xl font-semibold">{titulo}</legend>
      {children}
    </fieldset>
  )
}


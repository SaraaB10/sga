import { useEffect, useState } from 'react'
import { useFieldArray, useForm, useWatch } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { ArrowLeft, ImagePlus, Loader2, Trash2, TriangleAlert } from 'lucide-react'
import { toast } from 'sonner'
import { z } from 'zod'
import {
  useActualizarApartamento,
  useRegistrarApartamento,
  useTodasLasTemporadas,
  type ActualizacionApartamentoResponse,
  type TarifaRequest,
} from '@/api/apartamentos'
import { useApartamento } from '@/api/publico'
import { AvisoError, Campo, FotoApartamento } from '@/components/coral'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Skeleton } from '@/components/ui/skeleton'
import { claseTextarea } from '../calendario/estilos'
import {
  aPesos,
  entero,
  erroresDelServidor,
  esErrorDeCampos,
  MAXIMO_DESCRIPCION,
  pesosOpcional,
  textoObligatorio,
  textoOpcional,
  urlObligatoria,
} from '@/lib/validacion'

const MAXIMO_IMAGENES = 10
const MAXIMO_CARACTERISTICAS = 20

/** Para comparar sin distinguir mayúsculas ni tildes, como el servidor */
const sinTildes = (texto: string) =>
  texto
    .normalize('NFD')
    .replace(/\p{M}/gu, '')
    .toLowerCase()

/*
 * RegistrarApartamentoRequest y ActualizarApartamentoRequest: identificación (solo al registrar), nombre,
 * dormitorios, capacidad y dotación obligatorios; al registrar, entre 1 y 10 imágenes y tarifas opcionales mayores
 * que cero.
 */
const base = z.object({
  identificacion: z.string().trim(),
  nombre: textoObligatorio('Escribe el nombre', 100, 'El nombre admite máximo 100 caracteres'),
  descripcion: textoOpcional(MAXIMO_DESCRIPCION, 'La descripción admite máximo 1000 caracteres'),
  dormitorios: entero('Escribe un número entero de 1 o más dormitorios', { minimo: 1 }),
  capacidadMaxima: entero('Escribe una capacidad entera de 1 o más personas', { minimo: 1 }),
  dotacion: z
    .string()
    .refine((v) => lineas(v).length > 0, 'Escribe al menos un elemento')
    .refine((v) => lineas(v).every((l) => l.length <= 100), 'Cada elemento de la dotación admite máximo 100 caracteres'),
  // Opcionales: rasgos del apartamento, aparte de la dotación (las mismas reglas que valida el servidor)
  caracteristicas: z
    .string()
    .refine((v) => lineas(v).length <= MAXIMO_CARACTERISTICAS, `Máximo ${MAXIMO_CARACTERISTICAS} características`)
    .refine(
      (v) => lineas(v).every((l) => l.length >= 2 && l.length <= 60),
      'Cada característica debe tener entre 2 y 60 caracteres',
    )
    .refine((v) => {
      const claves = lineas(v).map(sinTildes)
      return new Set(claves).size === claves.length
    }, 'Hay características repetidas'),
  imagenes: z.array(z.object({ url: z.string() })),
  principal: z.string(),
  tarifas: z.record(z.string(), z.string()),
})

const esquemaEdicion = base

const esquemaRegistro = base.extend({
  identificacion: textoObligatorio(
    'Escribe la identificación, por ejemplo APT-401',
    20,
    'La identificación admite máximo 20 caracteres',
  ),
  imagenes: z
    .array(z.object({ url: urlObligatoria('Escribe la URL de la imagen') }))
    .min(1, 'Agrega al menos una imagen')
    .max(MAXIMO_IMAGENES, `Máximo ${MAXIMO_IMAGENES} imágenes`),
  tarifas: z.record(z.string(), pesosOpcional('Valor mayor que cero, sin decimales')),
})

type Datos = z.infer<typeof base>

function lineas(texto: string): string[] {
  return texto
    .split('\n')
    .map((l) => l.trim())
    .filter(Boolean)
}

const VACIO: Datos = {
  identificacion: '',
  nombre: '',
  descripcion: '',
  dormitorios: '1',
  capacidadMaxima: '2',
  dotacion: '',
  caracteristicas: '',
  imagenes: [{ url: '' }],
  principal: '0',
  tarifas: {},
}

export default function FormularioApartamento() {
  const { id } = useParams()
  const editando = !!id
  const navigate = useNavigate()
  const existente = useApartamento(id)
  const temporadas = useTodasLasTemporadas()
  const registrar = useRegistrarApartamento()
  const actualizar = useActualizarApartamento(id ?? '')
  const mutacion = editando ? actualizar : registrar
  const [resultado, setResultado] = useState<ActualizacionApartamentoResponse | null>(null)

  const {
    register,
    control,
    handleSubmit,
    reset,
    setError,
    setValue,
    formState: { errors },
  } = useForm<Datos>({ resolver: zodResolver(editando ? esquemaEdicion : esquemaRegistro), defaultValues: VACIO })
  const imagenes = useFieldArray({ control, name: 'imagenes' })
  const principal = useWatch({ control, name: 'principal' })
  const urls = useWatch({ control, name: 'imagenes' })

  // Al editar, el formulario arranca con los datos actuales
  const datosActuales = existente.data
  useEffect(() => {
    if (!datosActuales) return
    reset({
      ...VACIO,
      identificacion: datosActuales.identificacion,
      nombre: datosActuales.nombre,
      descripcion: datosActuales.descripcion ?? '',
      dormitorios: String(datosActuales.dormitorios),
      capacidadMaxima: String(datosActuales.capacidadMaxima),
      dotacion: datosActuales.dotacion.join('\n'),
      caracteristicas: (datosActuales.caracteristicas ?? []).join('\n'),
      // Las imágenes no se editan aquí
      imagenes: [],
    })
  }, [datosActuales, reset])

  const enviar = (datos: Datos) => {
    const comunes = {
      nombre: datos.nombre.trim(),
      descripcion: datos.descripcion.trim() || null,
      dormitorios: Number(datos.dormitorios),
      capacidadMaxima: Number(datos.capacidadMaxima),
      dotacion: lineas(datos.dotacion),
      caracteristicas: lineas(datos.caracteristicas),
    }

    if (editando) {
      actualizar.mutate(comunes, {
        onSuccess: (respuesta) => {
          if (respuesta.reservasSobreCapacidad.length > 0) {
            setResultado(respuesta)
            window.scrollTo({ top: 0, behavior: 'smooth' })
          } else {
            toast.success('Apartamento actualizado', { description: respuesta.apartamento.identificacion })
            navigate(`/panel/apartamentos/${encodeURIComponent(respuesta.apartamento.identificacion)}`)
          }
        },
        onError: (error) => marcarErrores(error),
      })
      return
    }

    // El esquema del registro ya validó la identificación, las imágenes y que cada tarifa escrita sea mayor que cero
    const tarifas: TarifaRequest[] = Object.entries(datos.tarifas)
      .filter(([, texto]) => !!texto?.trim())
      .map(([idTemporada, texto]) => ({ idTemporada, valorPorOcupante: aPesos(texto) }))

    const principal = Number(datos.principal)
    registrar.mutate(
      {
        ...comunes,
        identificacion: datos.identificacion.trim(),
        imagenes: datos.imagenes.map((img, i) => ({ url: img.url.trim(), principal: i === principal })),
        tarifas: tarifas.length > 0 ? tarifas : null,
      },
      {
        onSuccess: (creado) => {
          toast.success('Apartamento registrado', { description: `${creado.identificacion} nace inactivo hasta que lo actives.` })
          navigate(`/panel/apartamentos/${encodeURIComponent(creado.identificacion)}`)
        },
        onError: (error) => marcarErrores(error, tarifas.map((t) => t.idTemporada)),
      },
    )
  }

  /** Los 400 traen campo → mensaje: se muestran junto a cada campo */
  const marcarErrores = (error: unknown, temporadasDeTarifas: string[] = []) => {
    Object.entries(erroresDelServidor(error)).forEach(([campo, mensaje]) => {
      if (!mensaje) return
      // imagenes.0.url va a la URL de esa imagen; tarifas.0.x va a la tarifa de esa posición
      const imagen = /^imagenes\.(\d+)/.exec(campo)
      const tarifa = /^tarifas\.(\d+)/.exec(campo)
      if (imagen) setError(`imagenes.${Number(imagen[1])}.url`, { message: mensaje })
      else if (tarifa) {
        const idTemporada = temporadasDeTarifas[Number(tarifa[1])]
        if (idTemporada) setError(`tarifas.${idTemporada}`, { message: mensaje })
      } else {
        const raiz = campo.split('.')[0] as keyof Datos
        if (raiz in VACIO) setError(raiz, { message: mensaje })
      }
    })
  }

  const errorGeneral = mutacion.error && !esErrorDeCampos(mutacion.error) ? mutacion.error : null
  const rutaVolver = editando ? `/panel/apartamentos/${encodeURIComponent(id)}` : '/panel/apartamentos'

  if (editando && existente.error) {
    return (
      <section className="mx-auto w-full max-w-3xl">
        <AvisoError error={existente.error} />
      </section>
    )
  }
  if (editando && !existente.data) {
    return (
      <section className="mx-auto flex w-full max-w-3xl flex-col gap-4">
        <Skeleton className="h-10 w-64" />
        <Skeleton className="h-96 w-full" />
      </section>
    )
  }

  // Resultado de bajar la capacidad: reservas que quedan sobre la nueva capacidad
  if (resultado) {
    const apto = resultado.apartamento
    return (
      <section className="mx-auto flex w-full max-w-3xl flex-col gap-5">
        <h1 className="font-titulo text-3xl font-semibold">Apartamento actualizado</h1>
        <div
          role="alert"
          className="rounded-tarjeta border p-5"
          style={{ backgroundColor: '#FFF1C2', borderColor: '#F0D27A', color: '#6B4800' }}
        >
          <p className="flex items-center gap-2 font-semibold">
            <TriangleAlert aria-hidden="true" className="size-5" />
            Hay reservas con más personas que la nueva capacidad ({apto.capacidadMaxima})
          </p>
          {resultado.advertencia && <p className="mt-2 text-sm">{resultado.advertencia}</p>}
          <p className="mt-2 text-sm">Las reservas no cambian; revísalas con los huéspedes.</p>
          <ul className="mt-3 flex flex-wrap gap-2">
            {resultado.reservasSobreCapacidad.map((codigo) => (
              <li key={codigo}>
                <Link
                  to={`/panel/reservas/${encodeURIComponent(codigo)}`}
                  className="inline-flex min-h-11 items-center rounded-full bg-white/70 px-4 text-sm font-semibold underline-offset-2 hover:underline"
                >
                  {codigo}
                </Link>
              </li>
            ))}
          </ul>
        </div>
        <Button asChild className="w-fit">
          <Link to={`/panel/apartamentos/${encodeURIComponent(apto.identificacion)}`}>Ir al apartamento</Link>
        </Button>
      </section>
    )
  }

  return (
    <section className="mx-auto w-full max-w-3xl">
      <Link
        to={rutaVolver}
        className="inline-flex items-center gap-1.5 text-sm font-medium text-muted-foreground hover:text-foreground"
      >
        <ArrowLeft aria-hidden="true" className="size-4" /> Volver
      </Link>
      <h1 className="mt-2 font-titulo text-3xl font-semibold">
        {editando ? `Editar ${existente.data?.nombre ?? ''}` : 'Nuevo apartamento'}
      </h1>
      {!editando && (
        <p className="text-sm text-muted-foreground">
          Nace inactivo: no sale en la búsqueda hasta que lo actives con tarifa en todas las temporadas.
        </p>
      )}

      <form noValidate onSubmit={handleSubmit(enviar)} className="mt-6 flex flex-col gap-5">
        <fieldset className="flex flex-col gap-4 rounded-tarjeta border border-border bg-card p-4 shadow-tarjeta md:p-5">
          <legend className="sr-only">Datos</legend>
          <div className="grid gap-4 sm:grid-cols-2">
            <Campo id="identificacion" etiqueta="Identificación" error={errors.identificacion?.message}>
              <Input
                placeholder="APT-401"
                maxLength={20}
                disabled={editando}
                autoCapitalize="characters"
                {...register('identificacion')}
              />
            </Campo>
            <Campo id="nombre" etiqueta="Nombre" error={errors.nombre?.message}>
              <Input placeholder="Coral Esmeralda" maxLength={100} {...register('nombre')} />
            </Campo>
          </div>
          <Campo id="descripcion" etiqueta="Descripción (opcional)" error={errors.descripcion?.message}>
            <textarea className={claseTextarea} maxLength={MAXIMO_DESCRIPCION} {...register('descripcion')} />
          </Campo>
          <div className="grid grid-cols-2 gap-4">
            <Campo id="dormitorios" etiqueta="Dormitorios" error={errors.dormitorios?.message}>
              <Input type="number" min={1} inputMode="numeric" {...register('dormitorios')} />
            </Campo>
            <Campo
              id="capacidadMaxima"
              etiqueta="Capacidad máxima"
              ayuda={editando ? 'Si la bajas, te mostramos las reservas que la superan.' : undefined}
              error={errors.capacidadMaxima?.message}
            >
              <Input type="number" min={1} inputMode="numeric" {...register('capacidadMaxima')} />
            </Campo>
          </div>
          <Campo
            id="dotacion"
            etiqueta="Dotación"
            ayuda="Lo que trae el apartamento, un elemento por línea"
            error={errors.dotacion?.message}
          >
            <textarea
              className={claseTextarea}
              placeholder={'Cama extragrande\nCocina integral\nTina de hidromasaje'}
              {...register('dotacion')}
            />
          </Campo>
          <Campo
            id="caracteristicas"
            etiqueta="Características (opcional)"
            ayuda={`Rasgos del apartamento, una por línea (máximo ${MAXIMO_CARACTERISTICAS}). Se ofrecen en el filtro de la búsqueda.`}
            error={errors.caracteristicas?.message}
          >
            <textarea
              className={claseTextarea}
              placeholder={'Vista al mar\nFrente a la playa\nBalcón\nAdmite mascotas'}
              {...register('caracteristicas')}
            />
          </Campo>
        </fieldset>

        {!editando && (
          <fieldset className="rounded-tarjeta border border-border bg-card p-4 shadow-tarjeta md:p-5">
            <legend className="float-left mb-1 font-titulo text-xl font-semibold">Imágenes</legend>
            <p className="clear-both text-xs text-muted-foreground">
              Entre 1 y {MAXIMO_IMAGENES}. Marca cuál es la principal.
            </p>
            <ul className="mt-4 flex flex-col gap-3">
              {imagenes.fields.map((campo, i) => (
                <li key={campo.id} className="flex flex-wrap items-start gap-x-3 gap-y-1 sm:flex-nowrap">
                  <FotoApartamento src={urls?.[i]?.url} alt="" className="mt-6 size-11 shrink-0 rounded-campo bg-crema" />
                  <Campo
                    id={`imagen-${i}`}
                    etiqueta={`URL de la imagen ${i + 1}`}
                    error={errors.imagenes?.[i]?.url?.message}
                    className="min-w-0 flex-1 basis-[calc(100%-3.5rem)] sm:basis-0"
                  >
                    <Input type="url" inputMode="url" placeholder="https://…" {...register(`imagenes.${i}.url`)} />
                  </Campo>
                  <label className="ml-14 flex min-h-11 shrink-0 cursor-pointer items-center gap-1.5 text-sm sm:mt-6 sm:ml-0">
                    <input
                      type="radio"
                      value={String(i)}
                      className="size-4 accent-[var(--coral-primario)]"
                      {...register('principal')}
                    />
                    Principal
                  </label>
                  <Button
                    type="button"
                    variant="ghost"
                    size="icon"
                    className="shrink-0 sm:mt-6"
                    aria-label={`Quitar la imagen ${i + 1}`}
                    disabled={imagenes.fields.length === 1}
                    onClick={() => {
                      // La principal sigue apuntando a la misma imagen; si se quita, pasa a la primera
                      const p = Number(principal)
                      imagenes.remove(i)
                      if (p === i) setValue('principal', '0')
                      else if (p > i) setValue('principal', String(p - 1))
                    }}
                  >
                    <Trash2 aria-hidden="true" />
                  </Button>
                </li>
              ))}
            </ul>
            <Button
              type="button"
              variant="outline"
              size="sm"
              className="mt-3"
              disabled={imagenes.fields.length >= MAXIMO_IMAGENES}
              onClick={() => imagenes.append({ url: '' })}
            >
              <ImagePlus aria-hidden="true" /> Agregar imagen
            </Button>
            {(errors.imagenes?.message ?? errors.imagenes?.root?.message) && (
              <p className="mt-2 text-xs font-medium text-error">
                {errors.imagenes?.message ?? errors.imagenes?.root?.message}
              </p>
            )}
          </fieldset>
        )}

        {!editando && (
          <fieldset className="rounded-tarjeta border border-border bg-card p-4 shadow-tarjeta md:p-5">
            <legend className="float-left mb-1 font-titulo text-xl font-semibold">Tarifas (opcional)</legend>
            <p className="clear-both text-xs text-muted-foreground">
              Por ocupante facturable y por noche. Sin tarifa en todas las temporadas no se puede activar.
            </p>
            <div className="mt-4 grid gap-4 sm:grid-cols-3">
              {(temporadas.data ?? []).map((t) => (
                <Campo
                  key={t.id}
                  id={`tarifa-${t.id}`}
                  etiqueta={`Temporada ${t.nombre.toLowerCase()}`}
                  error={errors.tarifas?.[t.id]?.message}
                >
                  <Input inputMode="numeric" placeholder="300000" {...register(`tarifas.${t.id}`)} />
                </Campo>
              ))}
            </div>
          </fieldset>
        )}

        {errorGeneral && <AvisoError error={errorGeneral} />}

        <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
          <Button asChild variant="outline">
            <Link to={rutaVolver}>Cancelar</Link>
          </Button>
          <Button type="submit" disabled={mutacion.isPending}>
            {mutacion.isPending && <Loader2 aria-hidden="true" className="animate-spin" />}
            {editando ? 'Guardar cambios' : 'Registrar apartamento'}
          </Button>
        </div>
      </form>
    </section>
  )
}

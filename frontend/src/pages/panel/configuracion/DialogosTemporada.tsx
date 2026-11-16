import { useState } from 'react'
import { Plus, Trash2 } from 'lucide-react'
import { toast } from 'sonner'
import { z } from 'zod'
import { useDetallesApartamentos, useTodosLosApartamentos } from '@/api/apartamentos'
import { useAgregarRangoTemporada, useCrearTemporada, type RangoFechasRequest } from '@/api/configuracion'
import type { TemporadaResponse, TipoTemporada } from '@/api/tipos'
import { AvisoError, Campo } from '@/components/coral'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Skeleton } from '@/components/ui/skeleton'
import {
  aPesos,
  erroresDelServidor,
  fecha,
  finDespuesDeInicio,
  pesos,
  textoObligatorio,
  unirErrores,
  useValidacion,
} from '@/lib/validacion'
import { DialogoFormulario } from '@/pages/panel/folio/DialogoFormulario'
import { useAlAbrir } from '@/pages/panel/folio/useAlAbrir'
import { claseSelect } from '@/pages/publico/comun/estilos'
import { COLOR_TEMPORADA, finExcluido, textoRango, TIPOS_TEMPORADA } from './utilidadesTemporada'

interface Base {
  abierto: boolean
  alCambiar: (abierto: boolean) => void
}

interface RangoEditable {
  clave: number
  inicio: string
  /** Último día incluido */
  fin: string
}

/*
 * RangoFechasRequest: las dos fechas obligatorias. En pantalla la fecha final va incluida, así que puede ser el
 * mismo día de la inicial; al enviar se convierte al formato del backend (final excluida).
 */
const esquemaRango = z
  .object({
    inicio: fecha('Escribe la fecha inicial'),
    fin: fecha('Escribe la fecha final'),
  })
  .superRefine(finDespuesDeInicio('inicio', 'fin', 'La fecha final no puede ser anterior a la inicial', true))

const aRangoBackend = (rango: { inicio: string; fin: string }): RangoFechasRequest => ({
  fechaInicio: rango.inicio,
  fechaFin: finExcluido(rango.fin),
})

/** CrearTemporadaRequest: id, nombre y tipo; rangos si no es la base y una tarifa por cada apartamento activo */
function esquemaTemporada(esBase: boolean, activos: string[]) {
  return z.object({
    id: textoObligatorio('Escribe un identificador, por ejemplo T-FESTIVAL'),
    nombre: textoObligatorio('Escribe el nombre'),
    tipo: z.enum(TIPOS_TEMPORADA as [TipoTemporada, ...TipoTemporada[]], 'Elige el tipo'),
    rangos: esBase
      ? z.array(z.unknown())
      : z.array(esquemaRango).min(1, 'Una temporada que no es base necesita al menos un rango'),
    tarifas: z.object(Object.fromEntries(activos.map((a) => [a, pesos('Valor mayor que cero, sin decimales')]))),
  })
}

const AYUDA_FIN = 'Último día de la temporada, incluido.'

// ---------- Crear temporada ----------

interface PropsCrear extends Base {
  temporadas: TemporadaResponse[]
}

export function DialogoCrearTemporada({ temporadas, abierto, alCambiar }: PropsCrear) {
  const crear = useCrearTemporada()
  const apartamentos = useTodosLosApartamentos()
  const detalles = useDetallesApartamentos(abierto ? (apartamentos.data ?? []).map((a) => a.identificacion) : [])
  const cargandoApartamentos = apartamentos.isPending || detalles.some((d) => d.isPending)
  const errorApartamentos = apartamentos.error ?? detalles.find((d) => d.error)?.error
  // La regla 7.4 exige tarifa de cada apartamento activo
  const activos = detalles.flatMap((d) => (d.data && d.data.activo && !d.data.eliminado ? [d.data] : []))

  const [id, setId] = useState('')
  const [nombre, setNombre] = useState('')
  const [tipo, setTipo] = useState<TipoTemporada>('MEDIA')
  const [rangos, setRangos] = useState<RangoEditable[]>([{ clave: 0, inicio: '', fin: '' }])
  const [tarifas, setTarifas] = useState<Record<string, string>>({})

  const hayBase = temporadas.some((t) => t.tipo === 'BASE')
  const esBase = tipo === 'BASE'
  const idsActivos = activos.map((a) => a.identificacion)
  const validacion = useValidacion(esquemaTemporada(esBase, idsActivos), {
    id,
    nombre,
    tipo,
    rangos,
    tarifas: Object.fromEntries(idsActivos.map((a) => [a, tarifas[a] ?? ''])),
  })
  // Los 400 del backend: rangos[i].x a su rango y tarifas[i].x a la tarifa del apartamento de esa posición
  const deServidor = erroresDelServidor(crear.error)
  const errores = unirErrores(validacion.errores, deServidor)
  const errorRango = (i: number) =>
    errores[`rangos.${i}.inicio`] ??
    errores[`rangos.${i}.fin`] ??
    errores[`rangos.${i}.fechaInicio`] ??
    errores[`rangos.${i}.fechaFin`] ??
    errores[`rangos.${i}`]
  const errorTarifa = (i: number, idApartamento: string) =>
    errores[`tarifas.${idApartamento}`] ??
    errores[`tarifas.${i}.valorPorOcupante`] ??
    errores[`tarifas.${i}.identificacionApartamento`] ??
    errores[`tarifas.${i}`]

  useAlAbrir(abierto, () => {
    setId('')
    setNombre('')
    setTipo('MEDIA')
    setRangos([{ clave: Date.now(), inicio: '', fin: '' }])
    setTarifas({})
    validacion.reiniciar()
    crear.reset()
  })

  const enviar = () => {
    const limpios = validacion.comprobar()
    if (!limpios) return

    crear.mutate(
      {
        id: limpios.id,
        nombre: limpios.nombre,
        tipo,
        rangos: esBase ? [] : rangos.map(aRangoBackend),
        tarifas: idsActivos.map((a) => ({ identificacionApartamento: a, valorPorOcupante: aPesos(tarifas[a] ?? '') })),
      },
      {
        onSuccess: (temporada) => {
          toast.success('Temporada creada', { description: `${temporada.nombre} (${temporada.id})` })
          alCambiar(false)
        },
      },
    )
  }

  return (
    <DialogoFormulario
      id="form-temporada"
      abierto={abierto}
      alCambiar={alCambiar}
      titulo="Nueva temporada"
      descripcion="Todo apartamento activo debe tener tarifa en todas las temporadas: la temporada se crea junto con la tarifa de cada uno, o no se crea."
      textoEnviar="Crear temporada"
      cargando={crear.isPending}
      error={crear.error}
      tituloError="No se pudo crear la temporada"
      className="sm:max-w-xl"
      alEnviar={enviar}
    >
      <div className="grid gap-4 sm:grid-cols-2">
        <Campo id="temporada-id" etiqueta="Identificador" error={errores.id}>
          <Input placeholder="T-FESTIVAL" value={id} onChange={(e) => setId(e.target.value.toUpperCase())} />
        </Campo>
        <Campo id="temporada-nombre" etiqueta="Nombre" error={errores.nombre}>
          <Input placeholder="Festival" value={nombre} onChange={(e) => setNombre(e.target.value)} />
        </Campo>
      </div>
      <Campo
        id="temporada-tipo"
        etiqueta="Tipo"
        error={errores.tipo}
        ayuda={
          esBase
            ? hayBase
              ? 'Ya hay una temporada base: solo puede haber una.'
              : 'La base no lleva rangos: cubre las fechas que no son de otra temporada.'
            : undefined
        }
      >
        <select className={claseSelect} value={tipo} onChange={(e) => setTipo(e.target.value as TipoTemporada)}>
          {TIPOS_TEMPORADA.map((t) => (
            <option key={t} value={t}>
              {COLOR_TEMPORADA[t].etiqueta}
              {t === 'BASE' && hayBase ? ' (ya existe)' : ''}
            </option>
          ))}
        </select>
      </Campo>

      {!esBase && (
        <fieldset className="flex flex-col gap-3">
          <legend className="mb-2 text-sm font-medium">Rangos de fechas</legend>
          {rangos.map((r, i) => (
            <div key={r.clave} className="flex flex-col gap-1.5 rounded-campo border border-border p-3">
              <div className="grid grid-cols-[1fr_1fr_auto] items-end gap-2">
                <Campo id={`rango-${r.clave}-inicio`} etiqueta="Desde">
                  <Input
                    type="date"
                    value={r.inicio}
                    onChange={(e) => setRangos((rs) => rs.map((x) => (x.clave === r.clave ? { ...x, inicio: e.target.value } : x)))}
                  />
                </Campo>
                <Campo id={`rango-${r.clave}-fin`} etiqueta="Hasta (incluido)">
                  <Input
                    type="date"
                    min={r.inicio || undefined}
                    value={r.fin}
                    onChange={(e) => setRangos((rs) => rs.map((x) => (x.clave === r.clave ? { ...x, fin: e.target.value } : x)))}
                  />
                </Campo>
                <Button
                  type="button"
                  variant="ghost"
                  size="icon"
                  aria-label={`Quitar el rango ${i + 1}`}
                  disabled={rangos.length === 1}
                  onClick={() => setRangos((rs) => rs.filter((x) => x.clave !== r.clave))}
                >
                  <Trash2 aria-hidden="true" />
                </Button>
              </div>
              {errorRango(i) && <p className="text-xs font-medium text-error">{errorRango(i)}</p>}
            </div>
          ))}
          {errores.rangos && <p className="text-xs font-medium text-error">{errores.rangos}</p>}
          <Button
            type="button"
            variant="outline"
            size="sm"
            className="w-fit"
            onClick={() => setRangos((rs) => [...rs, { clave: Date.now(), inicio: '', fin: '' }])}
          >
            <Plus aria-hidden="true" /> Agregar otro rango
          </Button>
          <p className="text-xs text-muted-foreground">{AYUDA_FIN}</p>
        </fieldset>
      )}

      <fieldset className="flex flex-col gap-3">
        <legend className="mb-1 text-sm font-medium">Tarifa por ocupante y noche</legend>
        <p className="text-xs text-muted-foreground">Una por cada apartamento activo. Rige desde hoy.</p>
        {errorApartamentos ? (
          <AvisoError titulo="No se pudieron consultar los apartamentos" error={errorApartamentos} />
        ) : cargandoApartamentos ? (
          <Skeleton className="h-24" />
        ) : activos.length === 0 ? (
          <p className="text-sm text-muted-foreground">No hay apartamentos activos.</p>
        ) : (
          <div className="grid gap-3 sm:grid-cols-2">
            {activos.map((a, i) => (
              <Campo
                key={a.identificacion}
                id={`tarifa-${a.identificacion}`}
                etiqueta={`${a.identificacion} · ${a.nombre}`}
                error={errorTarifa(i, a.identificacion)}
              >
                <Input
                  inputMode="numeric"
                  placeholder="450000"
                  value={tarifas[a.identificacion] ?? ''}
                  onChange={(e) => setTarifas((t) => ({ ...t, [a.identificacion]: e.target.value }))}
                />
              </Campo>
            ))}
          </div>
        )}
      </fieldset>
    </DialogoFormulario>
  )
}

// ---------- Agregar rango ----------

interface PropsRango extends Base {
  temporada: TemporadaResponse | null
}

export function DialogoAgregarRango({ temporada, abierto, alCambiar }: PropsRango) {
  const agregar = useAgregarRangoTemporada()
  const [inicio, setInicio] = useState('')
  const [fin, setFin] = useState('')
  const validacion = useValidacion(esquemaRango, { inicio, fin })
  const errores = unirErrores(
    validacion.errores,
    erroresDelServidor(agregar.error, { fechaInicio: 'inicio', fechaFin: 'fin' }),
  )

  useAlAbrir(abierto, () => {
    setInicio('')
    setFin('')
    validacion.reiniciar()
    agregar.reset()
  })

  if (!temporada) return null

  const enviar = () => {
    const limpios = validacion.comprobar()
    if (!limpios) return
    const rango = aRangoBackend(limpios)
    agregar.mutate(
      { id: temporada.id, rango },
      {
        onSuccess: () => {
          toast.success('Rango agregado', { description: `${temporada.nombre}: ${textoRango(rango)}` })
          alCambiar(false)
        },
      },
    )
  }

  return (
    <DialogoFormulario
      id="form-rango"
      abierto={abierto}
      alCambiar={alCambiar}
      titulo={`Agregar rango a ${temporada.nombre}`}
      descripcion="El rango no puede solaparse con otra temporada ni con los rangos que ya tiene."
      textoEnviar="Agregar rango"
      cargando={agregar.isPending}
      error={agregar.error}
      tituloError="No se pudo agregar el rango"
      alEnviar={enviar}
    >
      <div className="grid grid-cols-2 gap-3">
        <Campo id="rango-inicio" etiqueta="Desde" error={errores.inicio}>
          <Input type="date" value={inicio} onChange={(e) => setInicio(e.target.value)} />
        </Campo>
        <Campo id="rango-fin" etiqueta="Hasta (incluido)" error={errores.fin}>
          <Input type="date" min={inicio || undefined} value={fin} onChange={(e) => setFin(e.target.value)} />
        </Campo>
      </div>
      <p className="-mt-2 text-xs text-muted-foreground">{AYUDA_FIN}</p>
    </DialogoFormulario>
  )
}

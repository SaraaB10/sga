import { useState, type FormEvent, type ReactNode } from 'react'
import { Loader2, Save } from 'lucide-react'
import { toast } from 'sonner'
import { z } from 'zod'
import { useActualizarParametros, type ActualizarParametrosRequest } from '@/api/configuracion'
import type { ParametrosAlojamientoResponse } from '@/api/tipos'
import { AvisoError, Campo } from '@/components/coral'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import {
  aPesos,
  entero,
  erroresDelServidor,
  esErrorDeCampos,
  MAXIMO_MOTIVO,
  pesos,
  textoObligatorio,
  unirErrores,
  useValidacion,
} from '@/lib/validacion'
import { claseCasilla } from '@/pages/publico/comun/estilos'

/*
 * Parámetros del alojamiento (PUT /api/alojamiento/parametros). Los rangos son los del DTO del backend; las reglas
 * de negocio (por ejemplo, el porcentaje del anticipo cuando se exige) las confirma el dominio y llegan como 409.
 * Rigen de inmediato, sin reiniciar el servidor.
 */

interface Formulario {
  umbralEdadFacturable: string
  tiempoPreparacionHoras: string
  plazoConfirmacionHoras: string
  horaLimiteNoShow: string
  horaInicioLlegadaNocturna: string
  recargoRecepcionNocturna: string
  estanciaMinimaNoches: string
  estanciaMinimaActiva: boolean
  exigeAnticipo: boolean
  porcentajeAnticipo: string
  conceptoDepositoGarantia: string
  depositoGarantia: string
  aceptaMascotas: boolean
  cargoAseoMascota: string
  maximoMascotas: string
  pesoMaximoMascotaKg: string
  edadMinimaCuenta: string
}

type ClaveTexto = { [K in keyof Formulario]: Formulario[K] extends string ? K : never }[keyof Formulario]
type ClaveCasilla = { [K in keyof Formulario]: Formulario[K] extends boolean ? K : never }[keyof Formulario]

const texto = (n: number | null | undefined) => (n === null || n === undefined ? '' : String(n))
const hhmm = (hora: string | null) => (hora ? hora.slice(0, 5) : '')

const aFormulario = (p: ParametrosAlojamientoResponse): Formulario => ({
  umbralEdadFacturable: texto(p.umbralEdadFacturable),
  tiempoPreparacionHoras: texto(p.tiempoPreparacionHoras),
  plazoConfirmacionHoras: texto(p.plazoConfirmacionHoras),
  horaLimiteNoShow: hhmm(p.horaLimiteNoShow),
  horaInicioLlegadaNocturna: hhmm(p.horaInicioLlegadaNocturna),
  recargoRecepcionNocturna: texto(p.recargoRecepcionNocturna),
  estanciaMinimaNoches: texto(p.estanciaMinimaNoches),
  estanciaMinimaActiva: p.estanciaMinimaActiva,
  exigeAnticipo: p.exigeAnticipo,
  porcentajeAnticipo: texto(p.porcentajeAnticipo),
  conceptoDepositoGarantia: p.conceptoDepositoGarantia ?? '',
  depositoGarantia: texto(p.depositoGarantia),
  aceptaMascotas: p.aceptaMascotas,
  cargoAseoMascota: p.aceptaMascotas ? texto(p.cargoAseoMascota) : '',
  maximoMascotas: p.aceptaMascotas ? texto(p.maximoMascotas) : '',
  pesoMaximoMascotaKg: p.aceptaMascotas ? texto(p.pesoMaximoMascotaKg) : '',
  edadMinimaCuenta: texto(p.edadMinimaCuenta),
})

const HORA = /^\d{2}:\d{2}$/
const hora = (mensaje: string) => z.string().min(1, mensaje).regex(HORA, 'Escribe una hora válida')

const esquema = z
  .object({
    umbralEdadFacturable: entero('Entero entre 0 y 17 años', { minimo: 0, maximo: 17 }),
    tiempoPreparacionHoras: entero('Entero entre 0 y 24 horas', { minimo: 0, maximo: 24 }),
    plazoConfirmacionHoras: entero('Entero entre 1 y 168 horas', { minimo: 1, maximo: 168 }),
    horaLimiteNoShow: hora('Elige la hora límite de no-show'),
    horaInicioLlegadaNocturna: hora('Elige desde qué hora la llegada es nocturna'),
    recargoRecepcionNocturna: pesos('El recargo debe ser mayor que cero, sin decimales'),
    estanciaMinimaNoches: entero('Entero entre 1 y 30 noches', { minimo: 1, maximo: 30 }),
    estanciaMinimaActiva: z.boolean(),
    exigeAnticipo: z.boolean(),
    porcentajeAnticipo: z.string(),
    conceptoDepositoGarantia: textoObligatorio(
      'Escribe el concepto del depósito',
      MAXIMO_MOTIVO,
      'El concepto admite máximo 250 caracteres',
    ),
    depositoGarantia: pesos('El depósito debe ser mayor que cero, sin decimales'),
    aceptaMascotas: z.boolean(),
    cargoAseoMascota: z.string(),
    maximoMascotas: z.string(),
    pesoMaximoMascotaKg: z.string(),
    edadMinimaCuenta: entero('Entero entre 14 y 21 años', { minimo: 14, maximo: 21 }),
  })
  .superRefine((d, contexto) => {
    const marcar = (campo: keyof Formulario, esquemaCampo: z.ZodType<string>) => {
      const r = esquemaCampo.safeParse(d[campo])
      if (!r.success) contexto.addIssue({ code: 'custom', path: [campo], message: r.error.issues[0].message })
    }
    // El porcentaje solo cuenta si se exige anticipo; las mascotas, si se aceptan
    if (d.exigeAnticipo) marcar('porcentajeAnticipo', entero('Entero entre 1 y 100', { minimo: 1, maximo: 100 }))
    if (d.aceptaMascotas) {
      marcar('cargoAseoMascota', pesos('El cargo de aseo debe ser mayor que cero, sin decimales'))
      marcar('maximoMascotas', entero('Entero entre 1 y 10', { minimo: 1, maximo: 10 }))
      marcar('pesoMaximoMascotaKg', entero('Entero entre 1 y 100 kg', { minimo: 1, maximo: 100 }))
    }
  })

function aSolicitud(d: Formulario): ActualizarParametrosRequest {
  return {
    umbralEdadFacturable: Number(d.umbralEdadFacturable),
    tiempoPreparacionHoras: Number(d.tiempoPreparacionHoras),
    plazoConfirmacionHoras: Number(d.plazoConfirmacionHoras),
    horaLimiteNoShow: d.horaLimiteNoShow,
    horaInicioLlegadaNocturna: d.horaInicioLlegadaNocturna,
    recargoRecepcionNocturna: aPesos(d.recargoRecepcionNocturna),
    estanciaMinimaNoches: Number(d.estanciaMinimaNoches),
    estanciaMinimaActiva: d.estanciaMinimaActiva,
    exigeAnticipo: d.exigeAnticipo,
    porcentajeAnticipo: d.exigeAnticipo ? Number(d.porcentajeAnticipo) : 0,
    conceptoDepositoGarantia: d.conceptoDepositoGarantia.trim(),
    depositoGarantia: aPesos(d.depositoGarantia),
    aceptaMascotas: d.aceptaMascotas,
    cargoAseoMascota: d.aceptaMascotas ? aPesos(d.cargoAseoMascota) : 0,
    maximoMascotas: d.aceptaMascotas ? Number(d.maximoMascotas) : 0,
    pesoMaximoMascotaKg: d.aceptaMascotas ? Number(d.pesoMaximoMascotaKg) : 0,
    edadMinimaCuenta: Number(d.edadMinimaCuenta),
  }
}

export function ParametrosAlojamiento({ parametros }: { parametros: ParametrosAlojamientoResponse }) {
  const actualizar = useActualizarParametros()
  const [datos, setDatos] = useState<Formulario>(() => aFormulario(parametros))
  const validacion = useValidacion(esquema, datos)
  const errores = unirErrores(validacion.errores, erroresDelServidor(actualizar.error))

  const campo = (clave: ClaveTexto) => ({
    value: datos[clave],
    onChange: (e: { target: { value: string } }) => setDatos((d) => ({ ...d, [clave]: e.target.value })),
  })
  const casilla = (clave: ClaveCasilla) => ({
    checked: datos[clave],
    onChange: (e: { target: { checked: boolean } }) => setDatos((d) => ({ ...d, [clave]: e.target.checked })),
  })

  const enviar = (evento: FormEvent) => {
    evento.preventDefault()
    if (!validacion.comprobar()) return
    actualizar.mutate(aSolicitud(datos), {
      onSuccess: (alojamiento) => {
        setDatos(aFormulario(alojamiento.parametros))
        validacion.reiniciar()
        toast.success('Parámetros actualizados', { description: 'Rigen desde ya para las nuevas operaciones.' })
      },
    })
  }

  return (
    <form
      noValidate
      onSubmit={enviar}
      aria-labelledby="titulo-parametros"
      className="flex flex-col gap-6 rounded-tarjeta border border-border bg-card p-5 shadow-tarjeta md:p-6"
    >
      <div className="flex flex-col gap-1">
        <h2 id="titulo-parametros" className="font-titulo text-xl font-semibold">
          Parámetros
        </h2>
        <p className="text-sm text-muted-foreground">
          Rigen de inmediato para las cotizaciones, reservas y cuentas nuevas. Las reservas ya creadas conservan sus
          valores.
        </p>
      </div>

      <Grupo titulo="Huéspedes y cuentas">
        <Campo id="par-umbral" etiqueta="Edad desde la que se paga noche" error={errores.umbralEdadFacturable} ayuda="Los menores de esta edad no pagan.">
          <Input inputMode="numeric" {...campo('umbralEdadFacturable')} />
        </Campo>
        <Campo id="par-edad-cuenta" etiqueta="Edad mínima de la cuenta" error={errores.edadMinimaCuenta} ayuda="También para ser titular en recepción.">
          <Input inputMode="numeric" {...campo('edadMinimaCuenta')} />
        </Campo>
      </Grupo>

      <Grupo titulo="Plazos y horarios">
        <Campo id="par-preparacion" etiqueta="Tiempo de preparación (horas)" error={errores.tiempoPreparacionHoras}>
          <Input inputMode="numeric" {...campo('tiempoPreparacionHoras')} />
        </Campo>
        <Campo id="par-confirmacion" etiqueta="Plazo para confirmar y pagar el anticipo (horas)" error={errores.plazoConfirmacionHoras}>
          <Input inputMode="numeric" {...campo('plazoConfirmacionHoras')} />
        </Campo>
        <Campo id="par-no-show" etiqueta="Hora límite de no-show" error={errores.horaLimiteNoShow}>
          <Input type="time" {...campo('horaLimiteNoShow')} />
        </Campo>
        <Campo id="par-estancia-minima" etiqueta="Estancia mínima de fin de semana (noches)" error={errores.estanciaMinimaNoches}>
          <Input inputMode="numeric" {...campo('estanciaMinimaNoches')} />
        </Campo>
        <Casilla id="par-estancia-activa" etiqueta="Exigir la estancia mínima en temporada media y alta" {...casilla('estanciaMinimaActiva')} />
      </Grupo>

      <Grupo titulo="Recepción nocturna">
        <Campo id="par-hora-nocturna" etiqueta="Llegada nocturna desde" error={errores.horaInicioLlegadaNocturna}>
          <Input type="time" {...campo('horaInicioLlegadaNocturna')} />
        </Campo>
        <Campo id="par-recargo-nocturno" etiqueta="Recargo (pesos)" error={errores.recargoRecepcionNocturna}>
          <Input inputMode="numeric" {...campo('recargoRecepcionNocturna')} />
        </Campo>
      </Grupo>

      <Grupo titulo="Anticipo y depósito">
        <Casilla id="par-exige-anticipo" etiqueta="Exigir anticipo para confirmar" {...casilla('exigeAnticipo')} />
        <Campo id="par-porcentaje" etiqueta="Porcentaje del anticipo" error={errores.porcentajeAnticipo}>
          <Input inputMode="numeric" disabled={!datos.exigeAnticipo} {...campo('porcentajeAnticipo')} />
        </Campo>
        <Campo id="par-deposito" etiqueta="Depósito de garantía (pesos)" error={errores.depositoGarantia}>
          <Input inputMode="numeric" {...campo('depositoGarantia')} />
        </Campo>
        <Campo id="par-concepto" etiqueta="Concepto del depósito en el folio" error={errores.conceptoDepositoGarantia} className="sm:col-span-2">
          <Input maxLength={MAXIMO_MOTIVO} {...campo('conceptoDepositoGarantia')} />
        </Campo>
      </Grupo>

      <Grupo titulo="Mascotas">
        <Casilla id="par-mascotas" etiqueta="Se aceptan mascotas" {...casilla('aceptaMascotas')} />
        <Campo id="par-cargo-aseo" etiqueta="Cargo de aseo por estancia (pesos)" error={errores.cargoAseoMascota}>
          <Input inputMode="numeric" disabled={!datos.aceptaMascotas} {...campo('cargoAseoMascota')} />
        </Campo>
        <Campo id="par-max-mascotas" etiqueta="Máximo de mascotas" error={errores.maximoMascotas}>
          <Input inputMode="numeric" disabled={!datos.aceptaMascotas} {...campo('maximoMascotas')} />
        </Campo>
        <Campo id="par-peso-mascota" etiqueta="Peso máximo por mascota (kg)" error={errores.pesoMaximoMascotaKg}>
          <Input inputMode="numeric" disabled={!datos.aceptaMascotas} {...campo('pesoMaximoMascotaKg')} />
        </Campo>
      </Grupo>

      {actualizar.error && !esErrorDeCampos(actualizar.error) && (
        <AvisoError titulo="No se pudieron guardar los parámetros" error={actualizar.error} />
      )}
      <div className="flex justify-end">
        <Button type="submit" disabled={actualizar.isPending} className="w-full sm:w-auto">
          {actualizar.isPending ? <Loader2 aria-hidden="true" className="animate-spin" /> : <Save aria-hidden="true" />}
          Guardar parámetros
        </Button>
      </div>
    </form>
  )
}

function Grupo({ titulo, children }: { titulo: string; children: ReactNode }) {
  return (
    <fieldset className="flex flex-col gap-3">
      <legend className="mb-2 text-sm font-semibold tracking-wide text-muted-foreground uppercase">{titulo}</legend>
      <div className="grid gap-4 sm:grid-cols-2">{children}</div>
    </fieldset>
  )
}

function Casilla({
  id,
  etiqueta,
  checked,
  onChange,
}: {
  id: string
  etiqueta: string
  checked: boolean
  onChange: (e: { target: { checked: boolean } }) => void
}) {
  return (
    <label htmlFor={id} className="flex items-center gap-3 self-end py-2 text-sm sm:col-span-2">
      <input id={id} type="checkbox" className={claseCasilla} checked={checked} onChange={onChange} />
      {etiqueta}
    </label>
  )
}

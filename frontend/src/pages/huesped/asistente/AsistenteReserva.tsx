import { useState, type FormEvent, type ReactNode } from 'react'
import { useParams, useSearchParams } from 'react-router-dom'
import { ArrowLeft, ArrowRight, Check, Loader2, Plus, Trash2 } from 'lucide-react'
import { z } from 'zod'
import { useTodosLosApartamentos } from '@/api/apartamentos'
import { esErrorApi } from '@/api/cliente'
import { useApartamento, usePoliticaVigente } from '@/api/publico'
import { useCrearReserva, type CanalOrigen, type ReservaDetalleResponse } from '@/api/reservas'
import type { OcupanteRequest } from '@/api/tipos'
import { useAuth } from '@/auth/AuthContext'
import { AvisoError, Campo, Fecha, InputDigitos } from '@/components/coral'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Skeleton } from '@/components/ui/skeleton'
import { cn } from '@/lib/utils'
import {
  correo,
  DIGITOS_TELEFONO,
  documento,
  erroresDelServidor,
  fecha,
  fechaNoFutura,
  finDespuesDeInicio,
  MAXIMO_DIGITOS_DOCUMENTO,
  telefono,
  textoObligatorio,
  validar,
  type Errores,
} from '@/lib/validacion'
import { useCotizacion, useReglasAlojamiento, useTemporadasAlojamiento } from '@/pages/publico/comun/consultas'
import {
  contarTipos,
  fechaCuadraConTipo,
  leerEstancia,
  nombreTipo,
  ocupantesParaCotizar,
  rangoNacimiento,
  tipoDeFecha,
  type TipoHuesped,
} from '@/pages/publico/comun/estancia'
import { claseCasilla, claseSelect } from '@/pages/publico/comun/estilos'
import { EtiquetaPago } from '@/pages/publico/comun/EtiquetaPago'
import { ListaPolitica } from '@/pages/publico/comun/ListaPolitica'
import { SelectorMascotas } from '@/pages/publico/comun/SelectorMascotas'
import { TextoCargo } from '@/pages/publico/comun/TextoCargo'
import {
  avisoEstanciaMinima,
  conHora,
  conPunto,
  esFechaValida,
  formatearHora,
  horaDesde,
  hoyISO,
  nochesEntre,
  sumarDias,
  textoMenores,
} from '@/pages/publico/comun/reglas'
import { ExitoReserva } from './ExitoReserva'
import { ResumenReserva } from './ResumenReserva'

/*
 * Asistente de reserva en 3 pasos, el mismo para el huésped (/reservar/:id, canal PORTAL) y para la
 * recepción (/panel/reservas/nueva, canal DIRECTO). El administrador elige el canal.
 */

type Modo = 'huesped' | 'panel'
type Paso = 1 | 2 | 3

interface Acompanante {
  nombre: string
  fechaNacimiento: string
  /**
   * Paga noche o menor del umbral. '' mientras no se elige (acompañante nuevo); null si viene de la cotización y
   * aún no carga el umbral con el que se deduce de su fecha.
   */
  tipo: TipoHuesped | '' | null
}

/** Mensaje para una fecha de nacimiento que no corresponde al tipo elegido */
function mensajeFueraDeTipo(fecha: string, tipo: TipoHuesped, umbral: number | null): string {
  if (fecha > hoyISO()) return 'La fecha no puede ser futura.'
  if (umbral === null) return 'Revisa la fecha de nacimiento.'
  return tipo === 'paga'
    ? `Con esa fecha tendría menos de ${umbral} años a la llegada. Si es menor, cambia su tipo a "${nombreTipo('menor', umbral)}".`
    : `Con esa fecha tendría ${umbral} años o más a la llegada. Si paga noche, cambia su tipo a "${nombreTipo('paga', umbral)}".`
}

interface TitularFormulario {
  nombre: string
  tipoDocumento: string
  numeroDocumento: string
  email: string
  telefono: string
  fechaNacimiento: string
}

const PASOS: { numero: Paso; titulo: string }[] = [
  { numero: 1, titulo: 'Huéspedes' },
  { numero: 2, titulo: 'Detalles' },
  { numero: 3, titulo: 'Confirmar' },
]

const TIPOS_DOCUMENTO = [
  { valor: 'CC', texto: 'Cédula de ciudadanía' },
  { valor: 'CE', texto: 'Cédula de extranjería' },
  { valor: 'TI', texto: 'Tarjeta de identidad' },
  { valor: 'PA', texto: 'Pasaporte' },
  { valor: 'PPT', texto: 'Permiso de protección temporal' },
]

const NOMBRE_CANAL: Record<CanalOrigen, string> = { PORTAL: 'Portal web', DIRECTO: 'Directo (recepción)', EXTERNO: 'Externo' }

/** A qué paso pertenece un 409 del backend, según su mensaje */
function pasoDelError(mensaje: string): Paso {
  if (/L-16|mascota|RN-09|RP-03|hora|nocturn/i.test(mensaje)) return 2
  if (/RN-0|RN-20|RP-01|capacidad|fecha|noche|disponib|ocupante|edad|bloque|apartamento|estancia/i.test(mensaje)) return 1
  return 3
}

// ---------- Esquemas (CrearReservaRequest, TitularRequest y OcupanteRequest) ----------

/** Paso 1: apartamento y fechas válidas; la llegada no es pasada y la salida va después de la llegada */
const esquemaEstancia = z
  .object({
    apartamento: z.string().min(1, 'Elige el apartamento.'),
    entrada: fecha('Elige la fecha de llegada.').refine((f) => f >= hoyISO(), 'La llegada no puede ser en el pasado.'),
    salida: fecha('Elige la fecha de salida.'),
  })
  .superRefine(finDespuesDeInicio('entrada', 'salida', 'La salida debe ser después de la llegada.'))

/** Titular que escribe la recepción: todos los datos obligatorios, con correo y teléfono válidos */
const esquemaTitular = z.object({
  nombre: textoObligatorio('Escribe el nombre del titular.'),
  tipoDocumento: z.string().min(1, 'Elige el tipo de documento.'),
  numeroDocumento: documento('Escribe el número de documento.'),
  email: correo(),
  telefono: telefono('Escribe el teléfono.'),
  fechaNacimiento: fechaNoFutura('Escribe la fecha de nacimiento.'),
})

/** Acompañante: nombre y fecha de nacimiento no futura; el tipo se revisa aparte */
const esquemaAcompanante = z.object({
  nombre: textoObligatorio('Escribe el nombre.'),
  fechaNacimiento: fechaNoFutura('Escribe la fecha de nacimiento.'),
})

/** Paso 2: hora opcional y mascotas sin números negativos, con el peso de la más pesada si hay */
function esquemaDetalles(maximoMascotas: number | null, pesoMaximo: number | null) {
  return z
    .object({
      hora: z.string().refine((h) => h === '' || /^\d{2}:\d{2}(:\d{2})?$/.test(h), 'Escribe una hora válida.'),
      mascotas: z
        .number()
        .int()
        .min(0, 'La cantidad de mascotas no puede ser negativa.')
        .refine(
          (n) => maximoMascotas === null || n <= maximoMascotas,
          `Se aceptan máximo ${maximoMascotas} mascotas.`,
        ),
      peso: z.string(),
    })
    .superRefine((datos, contexto) => {
      if (datos.mascotas === 0) return
      const kg = Number(datos.peso)
      const mensaje =
        !/^\d+$/.test(datos.peso.trim()) || kg <= 0
          ? 'Escribe el peso de la mascota más pesada en kilos enteros, mayor que cero.'
          : pesoMaximo !== null && kg > pesoMaximo
            ? `Solo se aceptan mascotas de hasta ${pesoMaximo} kg.`
            : null
      if (mensaje) contexto.addIssue({ code: 'custom', path: ['peso'], message: mensaje })
    })
}

/** Paso 3: aceptar la política de cancelación */
const esquemaConfirmacion = z.object({
  acepta: z.boolean().refine((v) => v, 'Debes aceptar la política de cancelación.'),
})

/** Campo del titular → clave del error en pantalla */
const CAMPO_TITULAR: Record<string, string> = {
  nombre: 'titularNombre',
  tipoDocumento: 'titularTipo',
  numeroDocumento: 'titularDocumento',
  email: 'titularEmail',
  telefono: 'titularTelefono',
  fechaNacimiento: 'titularFecha',
}

/** Campo del backend → clave del error en pantalla */
const CAMPO_DEL_BACKEND: Record<string, string> = {
  identificacionApartamento: 'apartamento',
  fechaEntrada: 'entrada',
  fechaSalida: 'salida',
  horaEstimadaLlegada: 'hora',
  cantidadMascotas: 'mascotas',
  pesoMascotaMasPesadaKg: 'peso',
  canalOrigen: 'canal',
  ...Object.fromEntries(Object.entries(CAMPO_TITULAR).map(([campo, clave]) => [`titular.${campo}`, clave])),
}

/** Paso donde está cada campo; los acompañantes van en el 1 */
const PASO_DEL_CAMPO: Record<string, Paso> = {
  apartamento: 1,
  entrada: 1,
  salida: 1,
  canal: 1,
  ...Object.fromEntries(Object.values(CAMPO_TITULAR).map((clave) => [clave, 1 as Paso])),
  hora: 2,
  mascotas: 2,
  peso: 2,
}

/**
 * Errores por campo de un 400 al crear la reserva, con las claves de la pantalla. El ocupante 0 es el titular y
 * los demás son los acompañantes, en el mismo orden.
 */
function erroresDeCampos(error: unknown): Record<string, string> {
  const resultado: Record<string, string> = {}
  Object.entries(erroresDelServidor(error)).forEach(([campo, mensaje]) => {
    if (!mensaje) return
    const ocupante = /^ocupantes\.(\d+)\.(nombre|fechaNacimiento)$/.exec(campo)
    if (ocupante) {
      const i = Number(ocupante[1])
      const esNombre = ocupante[2] === 'nombre'
      const clave =
        i === 0 ? (esNombre ? 'titularNombre' : 'titularFecha') : `${esNombre ? 'acompNombre' : 'acompFecha'}${i - 1}`
      resultado[clave] = mensaje
    } else {
      resultado[CAMPO_DEL_BACKEND[campo] ?? campo] = mensaje
    }
  })
  return resultado
}

interface Props {
  modo: Modo
}

export function AsistenteReserva({ modo }: Props) {
  const { id: idRuta } = useParams()
  const [params] = useSearchParams()
  const { roles, usuario, cargandoUsuario } = useAuth()
  const { alojamiento, reglas } = useReglasAlojamiento()
  const { temporadas } = useTemporadasAlojamiento()
  const politica = usePoliticaVigente()
  const crear = useCrearReserva()

  const estanciaUrl = leerEstancia(params)
  const esAdmin = roles.includes('ADMINISTRADOR')

  // ---------- Estado ----------
  const [paso, setPaso] = useState<Paso>(1)
  const [idApartamento, setIdApartamento] = useState(
    modo === 'huesped' ? (idRuta ?? '') : (params.get('apartamento') ?? ''),
  )
  const [entrada, setEntrada] = useState(estanciaUrl.entrada)
  const [salida, setSalida] = useState(estanciaUrl.salida)
  const [canal, setCanal] = useState<CanalOrigen>(modo === 'huesped' ? 'PORTAL' : 'DIRECTO')
  const [titular, setTitular] = useState<TitularFormulario>({
    nombre: '',
    tipoDocumento: 'CC',
    numeroDocumento: '',
    email: '',
    telefono: '',
    fechaNacimiento: estanciaUrl.huespedes[0] ?? '',
  })
  const [acompanantes, setAcompanantes] = useState<Acompanante[]>(
    estanciaUrl.huespedes.slice(1).map((f) => ({ nombre: '', fechaNacimiento: f, tipo: null })),
  )
  // Cuántos acompañantes de cada tipo trajo la cotización; null si no se llegó desde una
  const [grupoCotizado, setGrupoCotizado] = useState<{ paga: number; menor: number } | null>(null)
  const [hora, setHora] = useState('')
  const [mascotas, setMascotas] = useState(0)
  const [peso, setPeso] = useState('')
  const [acepta, setAcepta] = useState(false)
  const [errores, setErrores] = useState<Record<string, string>>({})
  const [errorServidor, setErrorServidor] = useState<{ paso: Paso; error: unknown; titulo?: string } | null>(null)
  const [creada, setCreada] = useState<ReservaDetalleResponse | null>(null)

  const apartamento = useApartamento(idApartamento || undefined)
  const apartamentos = useTodosLosApartamentos()
  // Mientras el apartamento no carga no se limita el grupo; al cargar se valida con su capacidad
  const capacidad = apartamento.data?.capacidadMaxima ?? null

  // ---------- Tipo de cada acompañante ----------
  const umbral = reglas.umbralEdad
  const vieneDeCotizacion = params.getAll('h').length > 0
  // El tipo de los acompañantes de la cotización se deduce de su fecha en cuanto llega el umbral del alojamiento
  if (alojamiento.data !== undefined) {
    if (acompanantes.some((a) => a.tipo === null)) {
      setAcompanantes(
        acompanantes.map((a) =>
          a.tipo === null ? { ...a, tipo: tipoDeFecha(a.fechaNacimiento, umbral, estanciaUrl.entrada) } : a,
        ),
      )
    }
    if (vieneDeCotizacion && grupoCotizado === null) {
      setGrupoCotizado(
        contarTipos(estanciaUrl.huespedes.slice(1).map((f) => tipoDeFecha(f, umbral, estanciaUrl.entrada))),
      )
    }
  }
  const tipoDe = (a: Acompanante): TipoHuesped | '' =>
    a.tipo ?? tipoDeFecha(a.fechaNacimiento, umbral, estanciaUrl.entrada)
  const grupoActual = contarTipos(acompanantes.map(tipoDe))
  const grupoCambio =
    grupoCotizado !== null && (grupoActual.paga !== grupoCotizado.paga || grupoActual.menor !== grupoCotizado.menor)

  function quitarErroresDe(i: number) {
    setErrores((e) => {
      const resto = { ...e }
      delete resto[`acompTipo${i}`]
      delete resto[`acompFecha${i}`]
      return resto
    })
  }

  /** Cambia el tipo y borra la fecha si ya no le corresponde; el resumen se recalcula con el grupo nuevo */
  function cambiarTipo(i: number, tipo: TipoHuesped) {
    setAcompanantes(
      acompanantes.map((x, j) =>
        j === i
          ? {
              ...x,
              tipo,
              fechaNacimiento: fechaCuadraConTipo(x.fechaNacimiento, tipo, umbral, entrada) ? x.fechaNacimiento : '',
            }
          : x,
      ),
    )
    quitarErroresDe(i)
  }

  /**
   * Una fecha completa que no es del tipo elegido no se admite. Mientras se escribe el año (menos de 4 cifras)
   * se deja pasar para no borrar lo que se va digitando.
   */
  function cambiarFecha(i: number, fecha: string) {
    const tipo = tipoDe(acompanantes[i]) || 'paga'
    const completa = esFechaValida(fecha) && Number(fecha.slice(0, 4)) >= 1000
    const rechazada = completa && !fechaCuadraConTipo(fecha, tipo, umbral, entrada)
    setAcompanantes(acompanantes.map((x, j) => (j === i ? { ...x, fechaNacimiento: rechazada ? '' : fecha } : x)))
    quitarErroresDe(i)
    if (rechazada) setErrores((e) => ({ ...e, [`acompFecha${i}`]: mensajeFueraDeTipo(fecha, tipo, umbral) }))
  }

  // ---------- Ocupantes ----------
  const datosTitular: TitularFormulario =
    modo === 'huesped' && usuario
      ? {
          nombre: usuario.nombre,
          tipoDocumento: 'CC',
          numeroDocumento: usuario.documento,
          email: usuario.correo,
          telefono: usuario.telefono,
          fechaNacimiento: usuario.fechaNacimiento,
        }
      : titular

  const ocupantes: OcupanteRequest[] = [
    {
      nombre: datosTitular.nombre.trim(),
      tipoDocumento: datosTitular.tipoDocumento,
      numeroDocumento: datosTitular.numeroDocumento.trim(),
      fechaNacimiento: datosTitular.fechaNacimiento,
    },
    ...acompanantes.map((a) => ({ nombre: a.nombre.trim(), fechaNacimiento: a.fechaNacimiento })),
  ]

  const noches = nochesEntre(entrada, salida)
  const avisoRP01 = avisoEstanciaMinima(entrada, salida, temporadas, reglas)
  const sobreCapacidad = capacidad !== null && ocupantes.length > capacidad

  const cotizacion = useCotizacion(
    idApartamento && noches > 0 && !avisoRP01 && !sobreCapacidad
      ? {
          identificacionApartamento: idApartamento,
          fechaEntrada: entrada,
          fechaSalida: salida,
          ocupantes: ocupantesParaCotizar(
            [ocupantes[0], ...acompanantes.map((a) => ({ ...a, tipo: tipoDe(a) }))],
            umbral,
            entrada,
          ),
        }
      : null,
  )

  // ---------- Validación por paso ----------
  function validarPaso(p: Paso): Record<string, string> {
    const e: Record<string, string> = {}
    const anotar = (errores: Errores, renombrar: (clave: string) => string = (c) => c) =>
      Object.entries(errores).forEach(([clave, mensaje]) => {
        if (mensaje && e[renombrar(clave)] === undefined) e[renombrar(clave)] = mensaje
      })
    if (p === 1) {
      anotar(validar(esquemaEstancia, { apartamento: idApartamento, entrada, salida }).errores)
      if (avisoRP01) e.estancia = avisoRP01
      if (modo === 'panel') anotar(validar(esquemaTitular, titular).errores, (c) => CAMPO_TITULAR[c] ?? c)
      acompanantes.forEach((a, i) => {
        anotar(validar(esquemaAcompanante, a).errores, (c) => (c === 'nombre' ? `acompNombre${i}` : `acompFecha${i}`))
        const tipo = tipoDe(a)
        if (umbral !== null && tipo === '') e[`acompTipo${i}`] = 'Elige si paga noche o es menor.'
        else if (
          e[`acompFecha${i}`] === undefined &&
          !fechaCuadraConTipo(a.fechaNacimiento, tipo || 'paga', umbral, entrada)
        )
          e[`acompFecha${i}`] = mensajeFueraDeTipo(a.fechaNacimiento, tipo || 'paga', umbral)
      })
      if (sobreCapacidad) e.capacidad = `El apartamento recibe máximo ${capacidad} personas.`
    }
    if (p === 2) {
      anotar(validar(esquemaDetalles(reglas.maximoMascotas, reglas.pesoMaximoMascotaKg), { hora, mascotas, peso }).errores)
    }
    if (p === 3) anotar(validar(esquemaConfirmacion, { acepta }).errores)
    return e
  }

  function avanzar(e: FormEvent) {
    e.preventDefault()
    const nuevos = validarPaso(paso)
    setErrores(nuevos)
    if (Object.keys(nuevos).length > 0) return
    if (paso < 3) {
      setPaso((paso + 1) as Paso)
      window.scrollTo({ top: 0, behavior: 'smooth' })
    } else {
      enviar()
    }
  }

  function enviar() {
    setErrorServidor(null)
    crear.mutate(
      {
        identificacionApartamento: idApartamento,
        fechaEntrada: entrada,
        fechaSalida: salida,
        horaEstimadaLlegada: hora || undefined,
        canalOrigen: canal,
        titular: {
          nombre: datosTitular.nombre.trim(),
          tipoDocumento: datosTitular.tipoDocumento,
          numeroDocumento: datosTitular.numeroDocumento.trim(),
          email: datosTitular.email.trim(),
          telefono: datosTitular.telefono.trim(),
        },
        ocupantes,
        cantidadMascotas: mascotas,
        pesoMascotaMasPesadaKg: mascotas > 0 ? Number(peso) : 0,
      },
      {
        onSuccess: (reserva) => {
          setCreada(reserva)
          window.scrollTo({ top: 0, behavior: 'smooth' })
        },
        onError: (error) => {
          if (esErrorApi(error) && error.status === 403) {
            setErrorServidor({
              paso: 3,
              titulo: 'No puedes crear la reserva por este canal',
              error: new Error(
                modo === 'huesped'
                  ? 'Como huésped solo puedes reservar por el portal y a tu propio nombre: el titular debe ser el dueño de la cuenta. Si necesitas reservar para otra persona, comunícate con recepción.'
                  : 'La recepción solo crea reservas por el canal directo; el huésped reserva por el portal y a su nombre.',
              ),
            })
            return
          }
          // Los 400 por campo se muestran junto a su campo, en el paso donde está
          const deCampos = erroresDeCampos(error)
          if (Object.keys(deCampos).length > 0) {
            const destino = Object.keys(deCampos).some((c) => PASO_DEL_CAMPO[c] === 1 || /^acomp/.test(c))
              ? 1
              : Object.keys(deCampos).some((c) => PASO_DEL_CAMPO[c] === 2)
                ? 2
                : 3
            setErrores(deCampos)
            setErrorServidor({
              paso: destino,
              titulo: 'Revisa los datos marcados',
              error: new Error(Object.values(deCampos).join(' ')),
            })
            setPaso(destino)
            return
          }
          const destino = esErrorApi(error) && error.status === 409 ? pasoDelError(error.message) : 3
          setErrorServidor({ paso: destino, error })
          setPaso(destino)
        },
      },
    )
  }

  // ---------- Pantalla de éxito ----------
  if (creada) {
    return <ExitoReserva reserva={creada} modo={modo} reglas={reglas} apartamento={apartamento.data} />
  }

  if (modo === 'huesped' && cargandoUsuario) {
    return <Skeleton className="h-96 w-full" />
  }

  const errorDeEstePaso = errorServidor?.paso === paso ? errorServidor : null
  const erroresValidacion =
    errorDeEstePaso && esErrorApi(errorDeEstePaso.error) ? Object.values(errorDeEstePaso.error.errores) : []

  return (
    <div className="flex flex-col gap-6">
      <header className="flex flex-col gap-1">
        <h1 className="font-titulo text-3xl font-semibold md:text-4xl">
          {modo === 'huesped' ? 'Reserva tu estancia' : 'Nueva reserva'}
        </h1>
        {apartamento.data && (
          <p className="text-sm text-muted-foreground">
            {apartamento.data.identificacion} · {apartamento.data.nombre} · Canal {NOMBRE_CANAL[canal].toLowerCase()}
          </p>
        )}
      </header>

      <BarraPasos paso={paso} alIr={(p) => p < paso && setPaso(p)} />

      <div className="grid gap-6 lg:grid-cols-[1fr_22rem] lg:items-start">
        <form noValidate onSubmit={avanzar} className="flex flex-col gap-6">
          {errorDeEstePaso && (
            <AvisoError
              titulo={errorDeEstePaso.titulo ?? 'No se pudo crear la reserva'}
              error={errorDeEstePaso.error}
            />
          )}
          {erroresValidacion.length > 0 && (
            <ul className="list-disc pl-5 text-sm text-error">
              {erroresValidacion.map((m) => (
                <li key={m}>{m}</li>
              ))}
            </ul>
          )}

          {/* ---------- Paso 1: estancia y huéspedes ---------- */}
          {paso === 1 && (
            <>
              <Tarjeta titulo="Estancia">
                {modo === 'panel' && (
                  <div className="grid gap-4 sm:grid-cols-2">
                    <Campo id="res-apartamento" etiqueta="Apartamento" error={errores.apartamento}>
                      <select
                        className={claseSelect}
                        value={idApartamento}
                        onChange={(e) => setIdApartamento(e.target.value)}
                      >
                        <option value="">{apartamentos.isPending ? 'Cargando…' : 'Elige el apartamento'}</option>
                        {(apartamentos.data ?? []).map((a) => (
                          <option key={a.identificacion} value={a.identificacion}>
                            {a.identificacion} · {a.nombre} (hasta {a.capacidadMaxima})
                          </option>
                        ))}
                      </select>
                    </Campo>
                    {esAdmin && (
                      <Campo id="res-canal" etiqueta="Canal" error={errores.canal} ayuda="Portal: a nombre del huésped. Directo: en recepción.">
                        <select
                          className={claseSelect}
                          value={canal}
                          onChange={(e) => setCanal(e.target.value as CanalOrigen)}
                        >
                          <option value="DIRECTO">{NOMBRE_CANAL.DIRECTO}</option>
                          <option value="PORTAL">{NOMBRE_CANAL.PORTAL}</option>
                        </select>
                      </Campo>
                    )}
                  </div>
                )}
                <div className="grid gap-4 sm:grid-cols-2">
                  <Campo id="res-entrada" etiqueta="Llegada" error={errores.entrada}>
                    <Input
                      type="date"
                      min={hoyISO()}
                      value={entrada}
                      onChange={(e) => {
                        const v = e.target.value
                        setEntrada(v)
                        if (v && (!salida || salida <= v)) setSalida(sumarDias(v, 1))
                      }}
                    />
                  </Campo>
                  <Campo id="res-salida" etiqueta="Salida" error={errores.salida}>
                    <Input
                      type="date"
                      min={entrada ? sumarDias(entrada, 1) : hoyISO()}
                      value={salida}
                      onChange={(e) => setSalida(e.target.value)}
                    />
                  </Campo>
                </div>
                {avisoRP01 && <AvisoError titulo="Estancia mínima" mensaje={avisoRP01} />}
              </Tarjeta>

              <Tarjeta titulo="Titular de la reserva">
                {modo === 'huesped' ? (
                  usuario ? (
                    <dl className="grid gap-3 text-sm sm:grid-cols-2">
                      <Dato etiqueta="Nombre" valor={usuario.nombre} />
                      <Dato etiqueta="Documento" valor={usuario.documento} />
                      <Dato etiqueta="Correo" valor={usuario.correo} />
                      <Dato etiqueta="Teléfono" valor={usuario.telefono} />
                      <Dato etiqueta="Fecha de nacimiento" valor={<Fecha valor={usuario.fechaNacimiento} patron="d MMM yyyy" />} />
                      <div className="flex items-end">
                        <EtiquetaPago fecha={usuario.fechaNacimiento} umbral={reglas.umbralEdad} referencia={entrada} />
                      </div>
                    </dl>
                  ) : (
                    <AvisoError mensaje="No pudimos cargar tus datos. Recarga la página." />
                  )
                ) : (
                  <div className="grid gap-4 sm:grid-cols-2">
                    <Campo id="tit-nombre" etiqueta="Nombre completo" error={errores.titularNombre} className="sm:col-span-2">
                      <Input
                        autoComplete="off"
                        value={titular.nombre}
                        onChange={(e) => setTitular({ ...titular, nombre: e.target.value })}
                      />
                    </Campo>
                    <Campo id="tit-tipo" etiqueta="Tipo de documento" error={errores.titularTipo}>
                      <select
                        className={claseSelect}
                        value={titular.tipoDocumento}
                        onChange={(e) => setTitular({ ...titular, tipoDocumento: e.target.value })}
                      >
                        {TIPOS_DOCUMENTO.map((t) => (
                          <option key={t.valor} value={t.valor}>
                            {t.texto}
                          </option>
                        ))}
                      </select>
                    </Campo>
                    <Campo id="tit-documento" etiqueta="Número de documento" error={errores.titularDocumento}>
                      <InputDigitos
                        maximo={MAXIMO_DIGITOS_DOCUMENTO}
                        value={titular.numeroDocumento}
                        onChange={(e) => setTitular({ ...titular, numeroDocumento: e.target.value })}
                      />
                    </Campo>
                    <Campo id="tit-email" etiqueta="Correo" error={errores.titularEmail}>
                      <Input
                        type="email"
                        value={titular.email}
                        onChange={(e) => setTitular({ ...titular, email: e.target.value })}
                      />
                    </Campo>
                    <Campo id="tit-telefono" etiqueta="Teléfono" error={errores.titularTelefono}>
                      <InputDigitos
                        maximo={DIGITOS_TELEFONO}
                        value={titular.telefono}
                        onChange={(e) => setTitular({ ...titular, telefono: e.target.value })}
                      />
                    </Campo>
                    <Campo id="tit-fecha" etiqueta="Fecha de nacimiento" error={errores.titularFecha}>
                      <Input
                        type="date"
                        max={hoyISO()}
                        value={titular.fechaNacimiento}
                        onChange={(e) => setTitular({ ...titular, fechaNacimiento: e.target.value })}
                      />
                    </Campo>
                    <div className="flex items-end pb-2">
                      {titular.fechaNacimiento && (
                        <EtiquetaPago fecha={titular.fechaNacimiento} umbral={reglas.umbralEdad} referencia={entrada} />
                      )}
                    </div>
                  </div>
                )}
              </Tarjeta>

              <Tarjeta
                titulo={
                  capacidad !== null
                    ? `Ocupantes: ${ocupantes.length} de ${capacidad} (capacidad del apartamento)`
                    : `Ocupantes: ${ocupantes.length}`
                }
                descripcion={`El titular cuenta como ocupante. ${textoMenores(reglas.umbralEdad)}.`}
              >
                {grupoCambio && (
                  <AvisoError mensaje="El grupo cambió frente a la cotización; el valor se recalculó." />
                )}
                {acompanantes.length === 0 && (
                  <p className="text-sm text-muted-foreground">
                    Aún no agregas acompañantes.
                    {capacidad !== null &&
                      capacidad > ocupantes.length &&
                      ` Puedes agregar hasta ${capacidad - ocupantes.length} más.`}
                  </p>
                )}
                <ul className="flex flex-col gap-4">
                  {acompanantes.map((a, i) => {
                    const tipo = tipoDe(a)
                    const sinTipo = umbral !== null && tipo === ''
                    const rango = rangoNacimiento(tipo || 'paga', umbral, entrada)
                    return (
                      <li
                        key={i}
                        className="grid grid-cols-[1fr_auto] items-start gap-3 rounded-campo border border-border p-3 sm:grid-cols-[1fr_1fr_auto] lg:grid-cols-[1fr_9.5rem_10.5rem_auto]"
                      >
                        <Campo
                          id={`acomp-nombre-${i}`}
                          etiqueta={`Acompañante ${i + 1}`}
                          error={errores[`acompNombre${i}`]}
                          className="col-span-2 sm:col-span-3 lg:col-span-1"
                        >
                          <Input
                            placeholder="Nombre completo"
                            value={a.nombre}
                            onChange={(e) =>
                              setAcompanantes(acompanantes.map((x, j) => (j === i ? { ...x, nombre: e.target.value } : x)))
                            }
                          />
                        </Campo>
                        {umbral !== null && (
                          <Campo
                            id={`acomp-tipo-${i}`}
                            etiqueta="Tipo"
                            error={errores[`acompTipo${i}`]}
                            className="col-span-2 sm:col-span-1"
                          >
                            <select
                              className={claseSelect}
                              value={tipo}
                              onChange={(e) => cambiarTipo(i, e.target.value as TipoHuesped)}
                            >
                              {tipo === '' && (
                                <option value="" disabled>
                                  Elige el tipo
                                </option>
                              )}
                              <option value="paga">{nombreTipo('paga', umbral)}</option>
                              <option value="menor">{nombreTipo('menor', umbral)}</option>
                            </select>
                          </Campo>
                        )}
                        <Campo
                          id={`acomp-fecha-${i}`}
                          etiqueta="Nacimiento"
                          error={errores[`acompFecha${i}`]}
                          ayuda={sinTipo ? 'Primero elige el tipo.' : undefined}
                          className={umbral === null ? 'sm:col-span-2 lg:col-span-2' : undefined}
                        >
                          <Input
                            type="date"
                            min={rango.min}
                            max={rango.max}
                            disabled={sinTipo}
                            value={a.fechaNacimiento}
                            onChange={(e) => cambiarFecha(i, e.target.value)}
                          />
                        </Campo>
                        <Button
                          type="button"
                          variant="ghost"
                          size="icon"
                          className="mt-7"
                          aria-label={`Quitar al acompañante ${i + 1}`}
                          onClick={() => {
                            setAcompanantes(acompanantes.filter((_, j) => j !== i))
                            setErrores({})
                          }}
                        >
                          <Trash2 aria-hidden="true" />
                        </Button>
                      </li>
                    )
                  })}
                </ul>
                <Button
                  type="button"
                  variant="outline"
                  className="w-fit"
                  disabled={capacidad !== null && ocupantes.length >= capacidad}
                  onClick={() => setAcompanantes([...acompanantes, { nombre: '', fechaNacimiento: '', tipo: '' }])}
                >
                  <Plus aria-hidden="true" /> Agregar acompañante
                </Button>
                {errores.capacidad && <p className="text-sm font-medium text-error">{errores.capacidad}</p>}
              </Tarjeta>
            </>
          )}

          {/* ---------- Paso 2: llegada y mascotas ---------- */}
          {paso === 2 && (
            <>
              <Tarjeta titulo="Hora estimada de llegada">
                <Campo
                  id="res-hora"
                  error={errores.hora}
                  etiqueta="Hora"
                  ayuda={`${reglas.horaEntrada ? `${conPunto(conHora('La entrada es desde', reglas.horaEntrada))} ` : ''}Es necesaria para confirmar la reserva.`}
                  className="sm:max-w-60"
                >
                  <Input type="time" value={hora} onChange={(e) => setHora(e.target.value)} />
                </Campo>
                {reglas.horaNocturna && horaDesde(hora, reglas.horaNocturna) && (
                  <AvisoError
                    titulo="Llegada nocturna"
                    mensaje={
                      <>
                        Si llegas desde las {formatearHora(reglas.horaNocturna)},{' '}
                        <TextoCargo tipo="nocturna" valor={reglas.recargoNocturno} minuscula /> al folio.
                      </>
                    }
                  />
                )}
              </Tarjeta>

              <Tarjeta titulo="Mascotas">
                {reglas.aceptaMascotas !== false ? (
                  <>
                    <SelectorMascotas
                      id="res-mascotas"
                      etiqueta="¿Cuántas mascotas vienen?"
                      cantidad={mascotas}
                      alCambiar={setMascotas}
                      maximo={reglas.maximoMascotas}
                    />
                    {errores.mascotas && <p className="text-sm font-medium text-error">{errores.mascotas}</p>}
                    {mascotas > 0 && (
                      <>
                        <Campo
                          id="res-peso"
                          etiqueta="Peso de la más pesada (kg)"
                          error={errores.peso}
                          ayuda={reglas.pesoMaximoMascotaKg !== null ? `Máximo ${reglas.pesoMaximoMascotaKg} kg.` : undefined}
                          className="sm:max-w-60"
                        >
                          <Input
                            type="number"
                            inputMode="numeric"
                            min={1}
                            max={reglas.pesoMaximoMascotaKg ?? undefined}
                            value={peso}
                            onChange={(e) => setPeso(e.target.value)}
                          />
                        </Campo>
                        <AvisoError
                          titulo="Cargo de aseo"
                          mensaje={
                            <>
                              <TextoCargo tipo="aseo" valor={reglas.cargoAseoMascota} />, una vez por estancia.
                            </>
                          }
                        />
                      </>
                    )}
                  </>
                ) : (
                  <p className="text-sm text-muted-foreground">El alojamiento no acepta mascotas.</p>
                )}
              </Tarjeta>
            </>
          )}

          {/* ---------- Paso 3: confirmar ---------- */}
          {paso === 3 && (
            <>
              <Tarjeta titulo="Revisa tu reserva">
                <dl className="grid gap-4 text-sm sm:grid-cols-2">
                  <Dato
                    etiqueta="Apartamento"
                    valor={apartamento.data ? `${apartamento.data.identificacion} · ${apartamento.data.nombre}` : idApartamento}
                  />
                  <Dato
                    etiqueta="Estancia"
                    valor={
                      <>
                        <Fecha valor={entrada} /> → <Fecha valor={salida} /> · {noches} {noches === 1 ? 'noche' : 'noches'}
                      </>
                    }
                  />
                  <Dato etiqueta="Titular" valor={`${datosTitular.nombre} · ${datosTitular.tipoDocumento} ${datosTitular.numeroDocumento}`} />
                  <Dato etiqueta="Contacto" valor={`${datosTitular.email} · ${datosTitular.telefono}`} />
                  <Dato etiqueta="Hora de llegada" valor={hora ? formatearHora(hora) : 'Sin indicar'} />
                  <Dato
                    etiqueta="Mascotas"
                    valor={mascotas === 0 ? 'Ninguna' : `${mascotas} · la más pesada de ${peso} kg`}
                  />
                  <Dato etiqueta="Canal" valor={NOMBRE_CANAL[canal]} />
                </dl>
                <div>
                  <p className="mb-2 text-sm text-muted-foreground">Ocupantes</p>
                  <ul className="flex flex-col gap-2">
                    {ocupantes.map((o, i) => (
                      <li key={i} className="flex flex-wrap items-center justify-between gap-2 text-sm">
                        <span>
                          {o.nombre} {i === 0 && <span className="text-muted-foreground">(titular)</span>}
                        </span>
                        <EtiquetaPago fecha={o.fechaNacimiento} umbral={reglas.umbralEdad} referencia={entrada} />
                      </li>
                    ))}
                  </ul>
                </div>
              </Tarjeta>

              <Tarjeta titulo="Política de cancelación">
                {politica.data ? (
                  <ListaPolitica politica={politica.data} />
                ) : politica.error ? (
                  <AvisoError error={politica.error} />
                ) : (
                  <Skeleton className="h-16" />
                )}
                {politica.data?.consecuenciaNoShow && (
                  <p className="text-xs text-muted-foreground">Si no se presenta: {politica.data.consecuenciaNoShow}</p>
                )}
                <label className="flex items-start gap-3 rounded-campo bg-muted/60 p-3 text-sm">
                  <input
                    type="checkbox"
                    className={cn(claseCasilla, 'mt-0.5')}
                    checked={acepta}
                    onChange={(e) => setAcepta(e.target.checked)}
                    aria-invalid={errores.acepta ? true : undefined}
                  />
                  <span>
                    {modo === 'huesped' ? 'Acepto' : 'El titular acepta'} la política de cancelación
                    {politica.data ? ` (versión ${politica.data.version})` : ''} y las normas del alojamiento.
                  </span>
                </label>
                {errores.acepta && <p className="text-sm font-medium text-error">{errores.acepta}</p>}
              </Tarjeta>
            </>
          )}

          <div className="flex flex-wrap justify-between gap-3">
            {paso > 1 ? (
              <Button type="button" variant="outline" onClick={() => setPaso((paso - 1) as Paso)}>
                <ArrowLeft aria-hidden="true" /> Atrás
              </Button>
            ) : (
              <span />
            )}
            <Button type="submit" size="lg" disabled={crear.isPending}>
              {crear.isPending && <Loader2 aria-hidden="true" className="animate-spin" />}
              {paso < 3 ? (
                <>
                  Continuar <ArrowRight aria-hidden="true" />
                </>
              ) : (
                'Crear reserva'
              )}
            </Button>
          </div>
        </form>

        <ResumenReserva
          className="lg:sticky lg:top-24"
          apartamento={apartamento.data}
          entrada={entrada}
          salida={salida}
          cotizacion={noches > 0 && !avisoRP01 && !sobreCapacidad ? cotizacion.data : undefined}
          cargandoCotizacion={cotizacion.isFetching}
          errorCotizacion={cotizacion.error ?? apartamento.error ?? undefined}
          temporadas={temporadas}
          reglas={reglas}
          hora={hora}
          mascotas={mascotas}
          grupoCambio={grupoCambio}
        />
      </div>
    </div>
  )
}

// ---------- Piezas ----------

function BarraPasos({ paso, alIr }: { paso: Paso; alIr: (p: Paso) => void }) {
  return (
    <nav aria-label="Pasos de la reserva">
      <ol className="flex items-center gap-2">
        {PASOS.map((p, i) => {
          const hecho = p.numero < paso
          const actual = p.numero === paso
          return (
            <li key={p.numero} className="flex flex-1 items-center gap-2">
              <button
                type="button"
                onClick={() => alIr(p.numero)}
                disabled={!hecho}
                aria-current={actual ? 'step' : undefined}
                className="flex items-center gap-2 rounded-full text-left disabled:cursor-default"
              >
                <span
                  className={cn(
                    'flex size-9 shrink-0 items-center justify-center rounded-full border-2 text-sm font-semibold',
                    actual && 'border-primary bg-primary text-primary-foreground',
                    hecho && 'border-primary bg-card text-primary',
                    !actual && !hecho && 'border-border text-muted-foreground',
                  )}
                >
                  {hecho ? <Check aria-hidden="true" className="size-4" /> : p.numero}
                </span>
                <span className={cn('text-sm font-medium', actual ? 'text-foreground' : 'hidden text-muted-foreground sm:inline')}>
                  {p.titulo}
                </span>
              </button>
              {i < PASOS.length - 1 && (
                <span aria-hidden="true" className={cn('h-0.5 flex-1 rounded-full', hecho ? 'bg-primary' : 'bg-border')} />
              )}
            </li>
          )
        })}
      </ol>
    </nav>
  )
}

function Tarjeta({ titulo, descripcion, children }: { titulo: string; descripcion?: string; children: ReactNode }) {
  return (
    <section className="flex flex-col gap-4 rounded-tarjeta border border-border bg-card p-5 shadow-tarjeta">
      <div>
        <h2 className="font-titulo text-xl font-semibold">{titulo}</h2>
        {descripcion && <p className="text-sm text-muted-foreground">{descripcion}</p>}
      </div>
      {children}
    </section>
  )
}

function Dato({ etiqueta, valor }: { etiqueta: string; valor: ReactNode }) {
  return (
    <div className="flex flex-col gap-0.5">
      <dt className="text-xs text-muted-foreground">{etiqueta}</dt>
      <dd className="font-medium break-words">{valor}</dd>
    </div>
  )
}

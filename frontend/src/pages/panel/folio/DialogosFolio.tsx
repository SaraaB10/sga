import { useState } from 'react'
import { toast } from 'sonner'
import { z } from 'zod'
import { useRegistrarCargoServicio, useRegistrarCorreccion, useRegistrarPago } from '@/api/folio'
import { useMediosPago } from '@/api/publico'
import type { MedioPago } from '@/api/reservas'
import type { ServicioAdicionalResponse } from '@/api/tipos'
import { AvisoError, Campo, Dinero, formatearDinero } from '@/components/coral'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import {
  aPesos,
  erroresDelServidor,
  MAXIMO_MOTIVO,
  pesos,
  textoObligatorio,
  unirErrores,
  useValidacion,
} from '@/lib/validacion'
import { claseSelect, claseTextarea } from '@/pages/publico/comun/estilos'
import { DialogoFormulario } from './DialogoFormulario'
import { erroresDe } from './erroresDe'
import { type Movimiento } from './movimientos'
import { useAlAbrir } from './useAlAbrir'

export interface BaseDialogoFolio {
  codigo: string
  abierto: boolean
  alCambiar: (abierto: boolean) => void
}

// ---------- Registrar pago ----------

// RegistrarPagoRequest: valor positivo en pesos enteros y uno de los medios activos del catálogo (L-18)
function esquemaPago(activos: MedioPago[]) {
  return z.object({
    valor: pesos('El valor del pago debe ser mayor que cero, sin decimales'),
    medio: z.string().refine((m) => activos.includes(m), 'Elige el medio de pago'),
  })
}

export function DialogoPago({ codigo, saldo, abierto, alCambiar }: BaseDialogoFolio & { saldo: number }) {
  const pago = useRegistrarPago(codigo)
  const mediosPago = useMediosPago()
  const activos = (mediosPago.data ?? []).filter((m) => m.activo)
  const [valor, setValor] = useState('')
  const [elegido, setMedio] = useState<MedioPago>('')
  // Sin elección (o si el elegido se desactivó), el primero de los activos
  const medio = activos.some((m) => m.codigo === elegido) ? elegido : (activos[0]?.codigo ?? '')
  const validacion = useValidacion(
    esquemaPago(activos.map((m) => m.codigo)),
    { valor, medio },
  )
  const errores = unirErrores(validacion.errores, erroresDe(pago.error))

  useAlAbrir(abierto, () => {
    setValor('')
    setMedio('')
    validacion.reiniciar()
    pago.reset()
  })

  const enviar = () => {
    if (!validacion.comprobar()) return
    const monto = aPesos(valor)
    pago.mutate(
      { valor: monto, medio },
      {
        onSuccess: (folio) => {
          toast.success(`Pago de ${formatearDinero(monto)} registrado`, {
            description: `Nuevo saldo: ${formatearDinero(folio.saldo)}`,
          })
          alCambiar(false)
        },
      },
    )
  }

  return (
    <DialogoFormulario
      id="form-pago"
      abierto={abierto}
      alCambiar={alCambiar}
      titulo="Registrar pago"
      descripcion="Se admiten pagos parciales. Un pago registrado no se edita ni se borra."
      textoEnviar="Registrar pago"
      cargando={pago.isPending}
      error={pago.error}
      tituloError="No se pudo registrar el pago"
      alEnviar={enviar}
    >
      <Campo
        id="pago-valor"
        etiqueta="Valor en pesos"
        error={errores.valor}
        ayuda={
          saldo > 0 ? (
            <>
              Saldo pendiente: <Dinero valor={saldo} />.{' '}
              <Button type="button" variant="link" className="h-auto p-0 text-xs" onClick={() => setValor(String(saldo))}>
                Usar el saldo
              </Button>
            </>
          ) : undefined
        }
      >
        <Input inputMode="numeric" placeholder="310000" value={valor} onChange={(e) => setValor(e.target.value)} />
      </Campo>
      <Campo
        id="pago-medio"
        etiqueta="Medio de pago"
        error={errores.medio ?? (mediosPago.error ? 'No se pudieron consultar los medios de pago' : undefined)}
      >
        <select
          className={claseSelect}
          value={medio}
          disabled={activos.length === 0}
          onChange={(e) => setMedio(e.target.value)}
        >
          {activos.length === 0 && <option value="">{mediosPago.isPending ? 'Cargando…' : 'Sin medios activos'}</option>}
          {activos.map((m) => (
            <option key={m.codigo} value={m.codigo}>
              {m.nombre}
            </option>
          ))}
        </select>
      </Campo>
    </DialogoFormulario>
  )
}

// ---------- Cargar servicio adicional ----------

interface PropsServicio extends BaseDialogoFolio {
  servicios: ServicioAdicionalResponse[] | undefined
  errorServicios: unknown
}

export function DialogoServicio({ codigo, servicios, errorServicios, abierto, alCambiar }: PropsServicio) {
  const cargo = useRegistrarCargoServicio(codigo)
  const disponibles = (servicios ?? []).filter((s) => s.activo && s.generaCargo)
  const [idServicio, setIdServicio] = useState('')
  // RegistrarCargoServicioRequest: el id del servicio, que debe ser uno de los ofrecidos
  const esquema = z.object({
    idServicio: z
      .string()
      .min(1, 'Elige un servicio')
      .refine((id) => disponibles.some((s) => s.id === id), 'Elige un servicio activo que genere cargo'),
  })
  const validacion = useValidacion(esquema, { idServicio })
  const error = validacion.errores.idServicio ?? erroresDe(cargo.error).idServicio

  useAlAbrir(abierto, () => {
    setIdServicio('')
    validacion.reiniciar()
    cargo.reset()
  })

  const elegido = disponibles.find((s) => s.id === idServicio)

  const enviar = () => {
    if (!validacion.comprobar() || !elegido) return
    cargo.mutate(elegido.id, {
      onSuccess: () => {
        toast.success('Servicio cargado al folio', { description: `${elegido.nombre}: ${formatearDinero(elegido.valor)}` })
        alCambiar(false)
      },
    })
  }

  return (
    <DialogoFormulario
      id="form-servicio"
      abierto={abierto}
      alCambiar={alCambiar}
      titulo="Cargar servicio adicional"
      descripcion="Se asienta con el valor del servicio y la fecha de hoy. Si después hay que quitarlo, se corrige con un movimiento inverso."
      textoEnviar="Cargar al folio"
      cargando={cargo.isPending}
      error={cargo.error}
      tituloError="No se pudo cargar el servicio"
      alEnviar={enviar}
    >
      {errorServicios ? (
        <AvisoError titulo="No se pudieron consultar los servicios" error={errorServicios} />
      ) : !servicios ? (
        <p className="text-sm text-muted-foreground">Cargando servicios…</p>
      ) : disponibles.length === 0 ? (
        <AvisoError mensaje="El alojamiento no tiene servicios activos que generen cargo." />
      ) : (
        <>
          <Campo id="servicio-id" etiqueta="Servicio" error={error}>
            <select className={claseSelect} value={idServicio} onChange={(e) => setIdServicio(e.target.value)}>
              <option value="">Elige un servicio</option>
              {disponibles.map((s) => (
                <option key={s.id} value={s.id}>
                  {s.nombre} · {formatearDinero(s.valor)}
                </option>
              ))}
            </select>
          </Campo>
          {elegido?.descripcion && <p className="-mt-2 text-sm text-muted-foreground">{elegido.descripcion}</p>}
        </>
      )}
    </DialogoFormulario>
  )
}

// ---------- Corrección de un cargo ----------

interface PropsCorreccion extends BaseDialogoFolio {
  /** Cargos que se pueden corregir */
  cargos: Movimiento[]
  /** Cargo elegido desde la tabla; null para elegirlo en el diálogo */
  indiceInicial: number | null
}

export function DialogoCorreccion({ codigo, cargos, indiceInicial, abierto, alCambiar }: PropsCorreccion) {
  const correccion = useRegistrarCorreccion(codigo)
  const [indice, setIndice] = useState('')
  const [motivo, setMotivo] = useState('')
  // RegistrarCorreccionRequest: la posición de un cargo del folio y el motivo
  const esquema = z.object({
    indice: z
      .string()
      .refine((valor) => cargos.some((c) => String(c.indiceCargo) === valor), 'Elige el cargo que vas a corregir'),
    motivo: textoObligatorio('Escribe el motivo de la corrección', MAXIMO_MOTIVO, 'El motivo admite máximo 250 caracteres'),
  })
  const validacion = useValidacion(esquema, { indice, motivo })
  const errores = unirErrores(
    validacion.errores,
    erroresDelServidor(correccion.error, { indiceCargo: 'indice' }),
  )

  useAlAbrir(abierto, () => {
    setIndice(indiceInicial !== null ? String(indiceInicial) : '')
    setMotivo('')
    validacion.reiniciar()
    correccion.reset()
  })

  const elegido = cargos.find((c) => String(c.indiceCargo) === indice)

  const enviar = () => {
    const limpios = validacion.comprobar()
    if (!limpios || !elegido) return
    correccion.mutate(
      { indiceCargo: elegido.indiceCargo as number, motivo: limpios.motivo },
      {
        onSuccess: () => {
          toast.success(`Corrección de ${elegido.numero} registrada`)
          alCambiar(false)
        },
      },
    )
  }

  return (
    <DialogoFormulario
      id="form-correccion"
      abierto={abierto}
      alCambiar={alCambiar}
      titulo="Registrar corrección"
      descripcion={
        <>
          Un cargo del folio <strong>no se edita ni se borra</strong>. La corrección asienta un movimiento inverso: un
          cargo del mismo tipo con el valor opuesto, el motivo y la fecha de hoy. Los dos quedan en el folio y se
          anulan entre sí.
        </>
      }
      textoEnviar="Asentar movimiento inverso"
      cargando={correccion.isPending}
      error={correccion.error}
      tituloError="No se pudo registrar la corrección"
      alEnviar={enviar}
    >
      <Campo id="correccion-cargo" etiqueta="Cargo a corregir" error={errores.indice}>
        <select className={claseSelect} value={indice} onChange={(e) => setIndice(e.target.value)}>
          <option value="">Elige un cargo</option>
          {cargos.map((c) => (
            <option key={c.clave} value={String(c.indiceCargo)}>
              {c.numero} · {c.concepto} · {formatearDinero(c.valor)}
            </option>
          ))}
        </select>
      </Campo>
      {elegido && (
        <p className="flex items-baseline justify-between gap-3 rounded-campo bg-muted/60 px-4 py-3 text-sm">
          <span>Movimiento inverso</span>
          <Dinero valor={-elegido.valor} className="font-semibold" />
        </p>
      )}
      <Campo id="correccion-motivo" etiqueta="Motivo" error={errores.motivo}>
        <textarea
          className={claseTextarea}
          placeholder="La cena no se consumió"
          value={motivo}
          onChange={(e) => setMotivo(e.target.value)}
        />
      </Campo>
    </DialogoFormulario>
  )
}

import { useState } from 'react'
import { Link } from 'react-router-dom'
import { CalendarClock, ReceiptText, ShieldCheck } from 'lucide-react'
import { toast } from 'sonner'
import { useAutorizarCierreFolio } from '@/api/folio'
import { useRegistrarSalida } from '@/api/ocupacion'
import { useFolio } from '@/api/reservas'
import { useAuth } from '@/auth/AuthContext'
import { AvisoError, Campo, ConfirmarDialogo, Dinero, formatearFecha } from '@/components/coral'
import { Skeleton } from '@/components/ui/skeleton'
import { MAXIMO_MOTIVO } from '@/lib/validacion'
import { claseTextarea } from '@/pages/publico/comun/estilos'
import { hoyISO } from '../calendario/fechas'

/** Lo que el diálogo necesita de la reserva (sirve el resumen de Recepción y el detalle de la reserva) */
export interface ReservaParaSalida {
  codigo: string
  apartamento: string
  nombreTitular: string
  fechaSalida: string
}

interface Props {
  /** Reserva EN_CURSO elegida; null cierra el diálogo */
  reserva: ReservaParaSalida | null
  alCerrar: () => void
}

const enlaceFolio = (codigo: string) => `/panel/reservas/${encodeURIComponent(codigo)}/folio`

/**
 * Registrar la salida de una reserva EN_CURSO, cualquier día: antes de la fecha de salida es una salida anticipada,
 * que se registra igual y no aplica la política de cancelación. La salida exige el folio cerrado (RN-17): si sigue
 * abierto, se explica cómo cerrarlo con un enlace al folio. El administrador puede, además, registrar la salida con
 * autorización: cierra el folio con su saldo indicando el motivo (CU-07) y luego registra la salida.
 */
export function DialogoSalida({ reserva, alCerrar }: Props) {
  const codigo = reserva?.codigo ?? ''
  const folio = useFolio(reserva ? codigo : undefined)
  const salida = useRegistrarSalida()
  const autorizar = useAutorizarCierreFolio(codigo)
  const { roles, usuario } = useAuth()
  const esAdministrador = roles.includes('ADMINISTRADOR')
  const [conAutorizacion, setConAutorizacion] = useState(false)
  const [motivo, setMotivo] = useState('')
  const [errorMotivo, setErrorMotivo] = useState<string | undefined>()

  const ocupado = salida.isPending || autorizar.isPending
  // Al cerrar queda limpio para la próxima reserva
  const cerrar = () => {
    salida.reset()
    autorizar.reset()
    setConAutorizacion(false)
    setMotivo('')
    setErrorMotivo(undefined)
    alCerrar()
  }

  const f = folio.data
  const folioAbierto = !!f && !f.cerrado
  const anticipada = !!reserva && hoyISO() < reserva.fechaSalida
  const puedeSalir = !!f && (f.cerrado || (esAdministrador && conAutorizacion))

  const registrarSalida = () =>
    reserva &&
    salida.mutate(reserva.codigo, {
      onSuccess: () => {
        toast.success(anticipada ? 'Salida anticipada registrada' : 'Salida registrada', {
          description: `${reserva.codigo} · ${reserva.apartamento}`,
        })
        cerrar()
      },
    })

  const confirmar = () => {
    if (!reserva || !f) return
    if (f.cerrado) {
      registrarSalida()
      return
    }
    // Solo el administrador llega aquí con el folio abierto: autoriza el cierre con saldo y registra la salida
    const texto = motivo.trim()
    if (!texto) {
      setErrorMotivo('Escribe el motivo de la autorización')
      return
    }
    if (texto.length > MAXIMO_MOTIVO) {
      setErrorMotivo(`El motivo admite máximo ${MAXIMO_MOTIVO} caracteres`)
      return
    }
    autorizar.mutate(
      { autorizadoPor: usuario?.nombre ?? 'Administrador', motivo: texto },
      { onSuccess: () => registrarSalida() },
    )
  }

  return (
    <ConfirmarDialogo
      abierto={!!reserva}
      alCambiar={(abierto) => !abierto && !ocupado && cerrar()}
      titulo={anticipada ? 'Registrar salida anticipada' : 'Registrar salida'}
      descripcion={
        reserva && (
          <>
            {reserva.nombreTitular} deja <strong>{reserva.apartamento}</strong>. La reserva {reserva.codigo} quedará
            finalizada y el apartamento pasará a pendiente de preparación.
          </>
        )
      }
      textoConfirmar={folioAbierto && conAutorizacion ? 'Autorizar cierre y registrar salida' : 'Registrar salida'}
      cargando={ocupado}
      deshabilitado={!puedeSalir}
      alConfirmar={confirmar}
    >
      <div className="flex flex-col gap-3 text-sm">
        {reserva && anticipada && (
          <div role="status" className="flex gap-2 rounded-campo border border-[#f0c987] bg-[#FFF4DF] p-3 text-[#6B4A0B]">
            <CalendarClock aria-hidden="true" className="mt-0.5 size-4 shrink-0" />
            <p>
              <strong>Salida anticipada.</strong> La reserva sale el{' '}
              {formatearFecha(reserva.fechaSalida, "EEEE d 'de' MMMM")}. Se registra como una salida: no aplica la
              política de cancelación.
            </p>
          </div>
        )}

        {folio.error ? (
          <AvisoError titulo="No se pudo revisar el folio" error={folio.error} />
        ) : !f ? (
          <Skeleton className="h-14" />
        ) : f.cerrado ? (
          <p className="text-muted-foreground">El folio ya está cerrado.</p>
        ) : (
          <div role="alert" className="flex flex-col gap-2 rounded-campo border border-border bg-muted/60 p-3">
            <p>
              <strong>Primero hay que cerrar el folio.</strong> Está abierto con saldo{' '}
              <Dinero valor={f.saldo} className="font-semibold" />
              {f.saldo < 0 && ' a favor del huésped'}.{' '}
              {f.depositoGarantia > 0 && !f.depositoReembolsado
                ? 'Registra los pagos pendientes, devuelve el depósito de garantía y cierra el folio en cero.'
                : 'Deja el saldo en cero y cierra el folio.'}
            </p>
            <Link
              to={enlaceFolio(reserva?.codigo ?? '')}
              className="inline-flex w-fit items-center gap-1 font-medium text-coral-texto hover:underline"
            >
              <ReceiptText aria-hidden="true" className="size-4" /> Ir al folio
            </Link>
          </div>
        )}

        {folioAbierto && esAdministrador && (
          <div className="flex flex-col gap-2">
            <label className="flex cursor-pointer items-start gap-2">
              <input
                type="checkbox"
                className="mt-0.5 size-4 accent-coral"
                checked={conAutorizacion}
                onChange={(e) => {
                  setConAutorizacion(e.target.checked)
                  setErrorMotivo(undefined)
                }}
              />
              <span>
                <span className="inline-flex items-center gap-1 font-medium">
                  <ShieldCheck aria-hidden="true" className="size-4" /> Salida con autorización
                </span>
                <span className="block text-xs text-muted-foreground">
                  Cierra el folio con su saldo y queda registrado que lo autorizas tú
                  {usuario?.nombre ? ` (${usuario.nombre})` : ''} y por qué.
                </span>
              </span>
            </label>
            {conAutorizacion && (
              <Campo id="salida-motivo" etiqueta="Motivo de la autorización" error={errorMotivo}>
                <textarea
                  className={claseTextarea}
                  maxLength={MAXIMO_MOTIVO}
                  value={motivo}
                  onChange={(e) => {
                    setMotivo(e.target.value)
                    setErrorMotivo(undefined)
                  }}
                />
              </Campo>
            )}
          </div>
        )}

        {autorizar.error && <AvisoError titulo="No se pudo autorizar el cierre" error={autorizar.error} />}
        {salida.error && <AvisoError titulo="No se pudo registrar la salida" error={salida.error} />}
      </div>
    </ConfirmarDialogo>
  )
}

import { useState } from 'react'
import { toast } from 'sonner'
import { z } from 'zod'
import { useAutorizarCierreFolio } from '@/api/folio'
import { Campo, Dinero } from '@/components/coral'
import { Input } from '@/components/ui/input'
import { textoObligatorio, unirErrores, useValidacion } from '@/lib/validacion'
import { claseTextarea } from '@/pages/publico/comun/estilos'
import { DialogoFormulario } from './DialogoFormulario'
import type { BaseDialogoFolio } from './DialogosFolio'
import { erroresDe } from './erroresDe'
import { useAlAbrir } from './useAlAbrir'

// ---------- Autorizar cierre con saldo (solo ADMINISTRADOR) ----------

// AutorizarCierreFolioRequest: quién autoriza y el motivo, los dos obligatorios
const esquema = z.object({
  autorizadoPor: textoObligatorio('Falta el nombre de quien autoriza'),
  motivo: textoObligatorio('El motivo es obligatorio para cerrar con saldo'),
})

interface PropsAutorizar extends BaseDialogoFolio {
  saldo: number
  /** Nombre del administrador que inició sesión */
  autorizadoPor: string
}

export function DialogoAutorizarCierre({ codigo, saldo, autorizadoPor, abierto, alCambiar }: PropsAutorizar) {
  const autorizar = useAutorizarCierreFolio(codigo)
  const [motivo, setMotivo] = useState('')
  const validacion = useValidacion(esquema, { autorizadoPor, motivo })
  const errores = unirErrores(validacion.errores, erroresDe(autorizar.error))

  useAlAbrir(abierto, () => {
    setMotivo('')
    validacion.reiniciar()
    autorizar.reset()
  })

  const enviar = () => {
    const limpios = validacion.comprobar()
    if (!limpios) return
    autorizar.mutate(
      { autorizadoPor: `${limpios.autorizadoPor} (administración)`, motivo: limpios.motivo },
      {
        onSuccess: () => {
          toast.success('Folio cerrado con autorización')
          alCambiar(false)
        },
      },
    )
  }

  return (
    <DialogoFormulario
      id="form-autorizar"
      abierto={abierto}
      alCambiar={alCambiar}
      titulo="Autorizar cierre con saldo"
      descripcion="El folio se cierra aunque tenga saldo. Queda registrado quién lo autorizó y por qué."
      textoEnviar="Autorizar y cerrar"
      cargando={autorizar.isPending}
      error={autorizar.error}
      tituloError="No se pudo autorizar el cierre"
      alEnviar={enviar}
    >
      <p className="flex items-baseline justify-between gap-3 rounded-campo bg-muted/60 px-4 py-3 text-sm">
        <span>{saldo < 0 ? 'Saldo a favor' : 'Saldo pendiente'}</span>
        <Dinero valor={Math.abs(saldo)} className="font-semibold" />
      </p>
      <Campo id="autorizar-por" etiqueta="Autoriza" error={errores.autorizadoPor}>
        <Input value={autorizadoPor} readOnly disabled />
      </Campo>
      <Campo id="autorizar-motivo" etiqueta="Motivo" error={errores.motivo}>
        <textarea
          className={claseTextarea}
          placeholder="Saldo de $5.000 por redondeo aceptado por el huésped"
          value={motivo}
          onChange={(e) => setMotivo(e.target.value)}
        />
      </Campo>
    </DialogoFormulario>
  )
}

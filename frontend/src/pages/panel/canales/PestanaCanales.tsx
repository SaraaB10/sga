import { useState } from 'react'
import { KeyRound, Network, PowerOff } from 'lucide-react'
import { toast } from 'sonner'
import {
  useCanales,
  useDesactivarCanal,
  useRegenerarCredencialCanal,
  type CanalVentaResponse,
  type CredencialCanalResponse,
} from '@/api/canales'
import {
  AvisoError,
  ConfirmarDialogo,
  Fecha,
  Paginador,
  TablaResponsive,
  Vacio,
  type ColumnaTabla,
} from '@/components/coral'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { EtiquetaActivo } from './etiquetas'
import { PATRON_FECHA_HORA } from './textos'

type Accion = 'regenerar' | 'desactivar' | null

/**
 * 2.4: el alojamiento vende por tres canales. Portal y Directo vienen con la aplicación (el huésped reserva desde el
 * portal y la recepción desde el panel): no se registran, no usan credencial y no se desactivan. Los externos son
 * los que el administrador registra con su credencial.
 */
interface CanalIntegrado {
  integrado: true
  nombre: string
  tipo: string
  detalle: string
}
type FilaCanal = CanalIntegrado | (CanalVentaResponse & { integrado: false })

const CANALES_INTEGRADOS: CanalIntegrado[] = [
  { integrado: true, nombre: 'portal', tipo: 'Portal', detalle: 'El huésped reserva desde el portal' },
  { integrado: true, nombre: 'directo', tipo: 'Directo', detalle: 'La recepción registra la reserva en el panel' },
]

/** Canales de venta: sin credenciales; la credencial nueva pasa al padre, que la muestra una vez */
export function PestanaCanales({ alEmitir }: { alEmitir: (credencial: CredencialCanalResponse) => void }) {
  const [pagina, setPagina] = useState(0)
  const consulta = useCanales(pagina)
  const regenerar = useRegenerarCredencialCanal()
  const desactivar = useDesactivarCanal()
  const [accion, setAccion] = useState<Accion>(null)
  const [elegido, setElegido] = useState<CanalVentaResponse | null>(null)

  const abrir = (tipo: Accion, canal: CanalVentaResponse) => {
    setElegido(canal)
    regenerar.reset()
    desactivar.reset()
    setAccion(tipo)
  }
  const ocupado = regenerar.isPending || desactivar.isPending

  const columnas: ColumnaTabla<FilaCanal>[] = [
    {
      clave: 'nombre',
      titulo: 'Canal',
      ocultarEnTarjeta: true,
      celda: (c) =>
        c.integrado ? (
          <span>
            <span className="font-semibold">{c.tipo}</span>
            <span className="block text-xs text-muted-foreground">{c.detalle}</span>
          </span>
        ) : (
          <span className="font-mono font-semibold">{c.nombre}</span>
        ),
    },
    { clave: 'tipo', titulo: 'Tipo', celda: (c) => (c.integrado ? c.tipo : 'Externo') },
    { clave: 'estado', titulo: 'Estado', celda: (c) => <EtiquetaActivo activo={c.integrado || c.activo} /> },
    {
      clave: 'creado',
      titulo: 'Creado',
      celda: (c) =>
        c.integrado ? (
          <span className="text-muted-foreground">Integrado en la aplicación</span>
        ) : (
          <Fecha valor={c.fechaCreacion} patron={PATRON_FECHA_HORA} />
        ),
    },
  ]

  // Portal y Directo encabezan la primera página; los externos siguen con su paginación
  const externos: FilaCanal[] = (consulta.data?.contenido ?? []).map((c) => ({ ...c, integrado: false as const }))
  const filas: FilaCanal[] = pagina === 0 ? [...CANALES_INTEGRADOS, ...externos] : externos

  return (
    <>
      {consulta.error ? (
        <AvisoError titulo="No se pudieron consultar los canales" error={consulta.error} />
      ) : !consulta.data ? (
        <Skeleton className="h-60" />
      ) : (
        <>
          <TablaResponsive
            descripcion="Canales de venta"
            columnas={columnas}
            filas={filas}
            claveFila={(c) => (c.integrado ? `integrado-${c.nombre}` : c.nombre)}
            tituloTarjeta={(c) => (
              <span className="flex items-center justify-between gap-2">
                {c.integrado ? <span>{c.tipo}</span> : <span className="font-mono">{c.nombre}</span>}
                <EtiquetaActivo activo={c.integrado || c.activo} />
              </span>
            )}
            acciones={(c) =>
              c.integrado ? (
                <span className="text-xs text-muted-foreground">Sin credencial</span>
              ) : c.activo ? (
                <div className="flex flex-wrap justify-end gap-1">
                  <Button variant="ghost" size="sm" onClick={() => abrir('regenerar', c)}>
                    <KeyRound aria-hidden="true" /> Regenerar credencial
                  </Button>
                  <Button variant="ghost" size="sm" className="text-error" onClick={() => abrir('desactivar', c)}>
                    <PowerOff aria-hidden="true" /> Desactivar
                  </Button>
                </div>
              ) : (
                <span className="text-xs text-muted-foreground">Sin acciones</span>
              )
            }
          />
          {consulta.data.totalElementos === 0 && (
            <Vacio
              icono={Network}
              titulo="Sin canales externos"
              descripcion="Crea el primer canal externo para que una plataforma de terceros envíe reservas."
            />
          )}
          <Paginador
            className="mt-3"
            pagina={pagina}
            totalPaginas={consulta.data.totalPaginas}
            totalElementos={consulta.data.totalElementos}
            alCambiar={setPagina}
          />
        </>
      )}

      <ConfirmarDialogo
        abierto={accion === 'regenerar'}
        alCambiar={(abierto) => !ocupado && !abierto && setAccion(null)}
        titulo={`Regenerar la credencial de ${elegido?.nombre ?? ''}`}
        descripcion="La credencial actual dejará de funcionar de inmediato. El canal tendrá que configurar la nueva para seguir enviando reservas."
        textoConfirmar="Regenerar"
        textoCancelar="Volver"
        cargando={regenerar.isPending}
        alConfirmar={() =>
          elegido &&
          regenerar.mutate(elegido.nombre, {
            onSuccess: (respuesta) => {
              setAccion(null)
              alEmitir(respuesta)
            },
          })
        }
      >
        {regenerar.error && <AvisoError titulo="No se pudo regenerar" error={regenerar.error} />}
      </ConfirmarDialogo>

      <ConfirmarDialogo
        abierto={accion === 'desactivar'}
        alCambiar={(abierto) => !ocupado && !abierto && setAccion(null)}
        titulo={`Desactivar ${elegido?.nombre ?? ''}`}
        descripcion="El canal no se borra: se conservan sus reservas, sus conflictos y su bitácora, pero sus peticiones se rechazarán desde ahora. No se puede volver a activar."
        textoConfirmar="Desactivar"
        textoCancelar="Volver"
        peligroso
        cargando={desactivar.isPending}
        alConfirmar={() =>
          elegido &&
          desactivar.mutate(elegido.nombre, {
            onSuccess: (c) => {
              setAccion(null)
              toast.success('Canal desactivado', { description: c.nombre })
            },
          })
        }
      >
        {desactivar.error && <AvisoError titulo="No se pudo desactivar" error={desactivar.error} />}
      </ConfirmarDialogo>
    </>
  )
}

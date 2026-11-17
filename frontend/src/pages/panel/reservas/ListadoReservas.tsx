import { useState, type FormEvent } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { ClipboardList, Plus, Receipt, SlidersHorizontal } from 'lucide-react'
import { z } from 'zod'
import { useTodosLosApartamentos } from '@/api/apartamentos'
import { useReservas, type CanalOrigen, type FiltrosReservas, type ReservaResumenResponse } from '@/api/reservas'
import type { EstadoReserva as Estado } from '@/api/tipos'
import {
  AvisoError,
  Campo,
  Dinero,
  EstadoReserva,
  Fecha,
  InputDigitos,
  Paginador,
  TablaResponsive,
  Vacio,
  type ColumnaTabla,
} from '@/components/coral'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Skeleton } from '@/components/ui/skeleton'
import {
  documentoOpcional,
  MAXIMO_DIGITOS_DOCUMENTO,
  rangoFiltro,
  textoOpcional,
  useValidacion,
} from '@/lib/validacion'
import { NOMBRE_CANAL } from '@/pages/huesped/reserva/formato'
import { EncabezadoFiltros } from '@/pages/publico/comun/EncabezadoFiltros'
import { claseContenidoPlegable, claseSelect } from '@/pages/publico/comun/estilos'
import { nochesEntre } from '@/pages/publico/comun/reglas'

// buscar llega también desde la búsqueda del encabezado del panel (/panel/reservas?buscar=...)
const CLAVES = ['buscar', 'estado', 'apartamento', 'canal', 'titular', 'desde', 'hasta'] as const
type Clave = (typeof CLAVES)[number]

const ESTADOS: { valor: Estado; texto: string }[] = [
  { valor: 'PENDIENTE', texto: 'Pendiente' },
  { valor: 'CONFIRMADA', texto: 'Confirmada' },
  { valor: 'EN_CURSO', texto: 'En curso' },
  { valor: 'FINALIZADA', texto: 'Finalizada' },
  { valor: 'CANCELADA', texto: 'Cancelada' },
  { valor: 'NO_SHOW', texto: 'No se presentó' },
]

const CANALES: CanalOrigen[] = ['PORTAL', 'DIRECTO', 'EXTERNO']

export default function ListadoReservas() {
  const [params, setParams] = useSearchParams()
  const pagina = Number(params.get('pagina') ?? 0) || 0
  const valores = Object.fromEntries(CLAVES.map((c) => [c, params.get(c) ?? ''])) as Record<Clave, string>

  const filtros: FiltrosReservas = {
    estado: (valores.estado || undefined) as Estado | undefined,
    apartamento: valores.apartamento || undefined,
    canal: (valores.canal || undefined) as CanalOrigen | undefined,
    titular: valores.titular.trim() || undefined,
    desde: valores.desde || undefined,
    hasta: valores.hasta || undefined,
    buscar: valores.buscar.trim() || undefined,
  }
  const consulta = useReservas(filtros, pagina)

  const aplicar = (nuevos: Record<Clave, string>) => {
    const siguientes = new URLSearchParams()
    CLAVES.forEach((c) => nuevos[c] && siguientes.set(c, nuevos[c]))
    setParams(siguientes)
  }

  const cambiarPagina = (nueva: number) => {
    const siguientes = new URLSearchParams(params)
    siguientes.set('pagina', String(nueva))
    setParams(siguientes)
  }

  const enlace = (codigo: string) => `/panel/reservas/${encodeURIComponent(codigo)}`

  const columnas: ColumnaTabla<ReservaResumenResponse>[] = [
    {
      clave: 'codigo',
      titulo: 'Código',
      ocultarEnTarjeta: true,
      celda: (r) => (
        <Link to={enlace(r.codigo)} className="font-semibold text-primary hover:underline">
          {r.codigo}
        </Link>
      ),
    },
    // Titular y estancia pueden partirse en dos líneas para que la tabla quepa junto a la barra lateral
    { clave: 'titular', titulo: 'Titular', className: 'whitespace-normal', celda: (r) => r.nombreTitular },
    { clave: 'apartamento', titulo: 'Apartamento', celda: (r) => r.apartamento },
    {
      clave: 'estancia',
      titulo: 'Fechas',
      className: 'whitespace-normal',
      celda: (r) => {
        const noches = nochesEntre(r.fechaEntrada, r.fechaSalida)
        return (
          <span>
            <span className="whitespace-nowrap">
              <Fecha valor={r.fechaEntrada} /> →
            </span>{' '}
            <span className="whitespace-nowrap">
              <Fecha valor={r.fechaSalida} />
            </span>
            <span className="block text-xs text-muted-foreground">
              {noches} {noches === 1 ? 'noche' : 'noches'} · {r.totalOcupantes}{' '}
              {r.totalOcupantes === 1 ? 'huésped' : 'huéspedes'}
            </span>
          </span>
        )
      },
    },
    { clave: 'canal', titulo: 'Canal', celda: (r) => NOMBRE_CANAL[r.canalOrigen] },
    { clave: 'estado', titulo: 'Estado', celda: (r) => <EstadoReserva estado={r.estado} /> },
    {
      clave: 'valor',
      titulo: 'Estancia',
      className: 'md:text-right',
      celda: (r) => <Dinero valor={r.valorTotal} className="font-semibold" />,
    },
  ]

  return (
    <section className="mx-auto flex w-full max-w-6xl flex-col gap-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="font-titulo text-3xl font-semibold">Reservas</h1>
          {consulta.data && (
            <p className="text-sm text-muted-foreground">
              {consulta.data.totalElementos} {consulta.data.totalElementos === 1 ? 'reserva' : 'reservas'} · de la más
              reciente a la más antigua
            </p>
          )}
        </div>
        <Button asChild>
          <Link to="/panel/reservas/nueva">
            <Plus aria-hidden="true" /> Nueva reserva
          </Link>
        </Button>
      </div>

      <FiltrosListado key={params.toString()} valores={valores} alAplicar={aplicar} />

      {/* En el celular las tarjetas de TablaResponsive van sueltas; desde md, la tabla dentro de una tarjeta */}
      <div className="md:rounded-tarjeta md:border md:border-border md:bg-card md:p-4 md:shadow-tarjeta">
        {consulta.error ? (
          <AvisoError error={consulta.error} />
        ) : !consulta.data ? (
          <div className="flex flex-col gap-2">
            {[0, 1, 2, 3].map((i) => (
              <Skeleton key={i} className="h-14 w-full" />
            ))}
          </div>
        ) : consulta.data.contenido.length === 0 ? (
          <Vacio icono={ClipboardList} titulo="No hay reservas" descripcion="Prueba con otros filtros." />
        ) : (
          <>
            <TablaResponsive
              columnas={columnas}
              filas={consulta.data.contenido}
              claveFila={(r) => r.codigo}
              tituloTarjeta={(r) => (
                <Link to={enlace(r.codigo)} className="text-primary hover:underline">
                  {r.codigo}
                </Link>
              )}
              acciones={(r) => (
                <div className="flex flex-wrap justify-end gap-1">
                  <Button asChild variant="ghost" size="sm">
                    <Link to={enlace(r.codigo)} aria-label={`Ver la reserva ${r.codigo}`}>
                      Ver
                    </Link>
                  </Button>
                  {/* Recepción y administración llegan al folio sin pasar por el detalle */}
                  <Button asChild variant="ghost" size="sm">
                    <Link to={`${enlace(r.codigo)}/folio`} aria-label={`Ver el folio de ${r.codigo}`}>
                      <Receipt aria-hidden="true" /> Ver folio
                    </Link>
                  </Button>
                </div>
              )}
              descripcion="Reservas del alojamiento"
            />
            <Paginador
              className="mt-4"
              pagina={pagina}
              totalPaginas={consulta.data.totalPaginas}
              totalElementos={consulta.data.totalElementos}
              alCambiar={cambiarPagina}
            />
          </>
        )}
      </div>
    </section>
  )
}

// Filtros de GET /api/reservas: búsqueda de hasta 100 caracteres, documento del titular de 6 a 10 dígitos y
// "llegada hasta" no antes de "desde"
const esquemaFiltros = rangoFiltro().safeExtend({
  buscar: textoOpcional(100, 'La búsqueda admite máximo 100 caracteres'),
  estado: z.string(),
  apartamento: z.string(),
  canal: z.string(),
  titular: documentoOpcional(),
})

function FiltrosListado({
  valores,
  alAplicar,
}: {
  valores: Record<Clave, string>
  alAplicar: (v: Record<Clave, string>) => void
}) {
  const [borrador, setBorrador] = useState(valores)
  const validacion = useValidacion(esquemaFiltros, borrador)
  const errores = validacion.errores
  // En el celular los filtros empiezan plegados para que la lista quede a la vista
  const [abierto, setAbierto] = useState(false)
  const apartamentos = useTodosLosApartamentos()
  const cambiar = (c: Clave, v: string) => setBorrador((b) => ({ ...b, [c]: v }))

  const enviar = (e: FormEvent) => {
    e.preventDefault()
    if (validacion.comprobar()) alAplicar(borrador)
  }

  const limpiar = () => {
    const vacios = Object.fromEntries(CLAVES.map((c) => [c, ''])) as Record<Clave, string>
    setBorrador(vacios)
    validacion.reiniciar()
    alAplicar(vacios)
  }

  return (
    <form
      noValidate
      onSubmit={enviar}
      aria-label="Filtrar reservas"
      className="grid gap-4 rounded-tarjeta border border-border bg-card p-4 shadow-tarjeta sm:grid-cols-2 lg:grid-cols-4"
    >
      <div className="col-span-full">
        <EncabezadoFiltros
          id="boton-filtros-reservas"
          controla="contenido-filtros-reservas"
          abierto={abierto}
          alCambiar={setAbierto}
          activos={Object.values(valores).filter((v) => v.trim() !== '').length}
          desde="md"
        />
      </div>
      <div id="contenido-filtros-reservas" className={claseContenidoPlegable(abierto, 'md')}>
      <Campo
        id="fr-buscar"
        etiqueta="Buscar"
        ayuda="Código de la reserva o nombre del titular"
        error={errores.buscar}
        className="sm:col-span-2"
      >
        <Input
          type="search"
          autoComplete="off"
          maxLength={100}
          placeholder="RES-2026-00001 o Laura"
          value={borrador.buscar}
          onChange={(e) => cambiar('buscar', e.target.value)}
        />
      </Campo>
      <Campo id="fr-estado" etiqueta="Estado">
        <select className={claseSelect} value={borrador.estado} onChange={(e) => cambiar('estado', e.target.value)}>
          <option value="">Todos</option>
          {ESTADOS.map((s) => (
            <option key={s.valor} value={s.valor}>
              {s.texto}
            </option>
          ))}
        </select>
      </Campo>
      <Campo id="fr-apartamento" etiqueta="Apartamento">
        <select
          className={claseSelect}
          value={borrador.apartamento}
          onChange={(e) => cambiar('apartamento', e.target.value)}
        >
          <option value="">Todos</option>
          {(apartamentos.data ?? []).map((a) => (
            <option key={a.identificacion} value={a.identificacion}>
              {a.identificacion} · {a.nombre}
            </option>
          ))}
        </select>
      </Campo>
      <Campo id="fr-canal" etiqueta="Canal">
        <select className={claseSelect} value={borrador.canal} onChange={(e) => cambiar('canal', e.target.value)}>
          <option value="">Todos</option>
          {CANALES.map((c) => (
            <option key={c} value={c}>
              {NOMBRE_CANAL[c]}
            </option>
          ))}
        </select>
      </Campo>
      <Campo id="fr-titular" etiqueta="Documento del titular" error={errores.titular}>
        <InputDigitos
          maximo={MAXIMO_DIGITOS_DOCUMENTO}
          value={borrador.titular}
          onChange={(e) => cambiar('titular', e.target.value)}
        />
      </Campo>
      <Campo id="fr-desde" etiqueta="Llegada desde" error={errores.desde}>
        <Input type="date" value={borrador.desde} onChange={(e) => cambiar('desde', e.target.value)} />
      </Campo>
      <Campo id="fr-hasta" etiqueta="Llegada hasta" error={errores.hasta}>
        <Input type="date" value={borrador.hasta} onChange={(e) => cambiar('hasta', e.target.value)} />
      </Campo>
      <div className="flex items-end gap-2 sm:col-span-2">
        <Button type="submit" className="flex-1 sm:flex-none">
          <SlidersHorizontal aria-hidden="true" /> Filtrar
        </Button>
        <Button type="button" variant="ghost" onClick={limpiar}>
          Limpiar
        </Button>
      </div>
      </div>
    </form>
  )
}

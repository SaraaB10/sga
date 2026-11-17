import { keepPreviousData, useMutation, useQueries, useQuery } from '@tanstack/react-query'
import { cliente } from './cliente'
import type { MedioPagoResponse } from './reservas'
import type {
  AlojamientoResponse,
  ApartamentoDetalleResponse,
  ApartamentoDisponibleResponse,
  ApartamentoResponse,
  CotizacionResponse,
  CotizarEstanciaRequest,
  DisponibilidadApartamentoResponse,
  FiltrosDisponibles,
  Pagina,
  PoliticaCancelacionResponse,
  TemporadaResponse,
} from './tipos'

/*
 * Consultas públicas: responden sin token, para el visitante y para cualquier rol.
 * Cada consulta tiene su función (para usarla donde se necesite) y su hook de React Query.
 * Las páginas empiezan en 0 y traen 10 elementos, como el backend.
 */

// ---------- Funciones ----------

export async function listarApartamentos(pagina = 0): Promise<Pagina<ApartamentoResponse>> {
  const { data } = await cliente.get<Pagina<ApartamentoResponse>>('/api/apartamentos', { params: { pagina } })
  return data
}

export async function buscarDisponibles(
  filtros: FiltrosDisponibles,
  pagina = 0,
): Promise<Pagina<ApartamentoDisponibleResponse>> {
  const { data } = await cliente.get<Pagina<ApartamentoDisponibleResponse>>('/api/apartamentos/disponibles', {
    params: { ...limpiar(filtros), pagina },
  })
  return data
}

export async function consultarApartamento(id: string): Promise<ApartamentoDetalleResponse> {
  const { data } = await cliente.get<ApartamentoDetalleResponse>(`/api/apartamentos/${encodeURIComponent(id)}`)
  return data
}

/**
 * Noches libres u ocupadas del apartamento en [desde, hasta), máximo 62 noches.
 * @param excluirReserva al modificar una reserva, sus noches no cuentan como ocupadas (personal o su titular)
 */
export async function consultarDisponibilidad(
  id: string,
  desde: string,
  hasta: string,
  excluirReserva?: string,
): Promise<DisponibilidadApartamentoResponse> {
  const { data } = await cliente.get<DisponibilidadApartamentoResponse>(
    `/api/apartamentos/${encodeURIComponent(id)}/disponibilidad`,
    { params: { desde, hasta, ...(excluirReserva ? { excluirReserva } : {}) } },
  )
  return data
}

/** Máximo de noches por consulta de disponibilidad */
const NOCHES_POR_CONSULTA = 62

const sumarDiasISO = (fecha: string, dias: number) => {
  const d = new Date(`${fecha}T00:00:00Z`)
  d.setUTCDate(d.getUTCDate() + dias)
  return d.toISOString().slice(0, 10)
}

/** true si todas las noches de [entrada, salida) están libres; consulta por tramos de 62 noches */
export async function estanciaLibre(
  id: string,
  entrada: string,
  salida: string,
  excluirReserva?: string,
): Promise<boolean> {
  for (let desde = entrada; desde < salida; desde = sumarDiasISO(desde, NOCHES_POR_CONSULTA)) {
    const hasta = sumarDiasISO(desde, NOCHES_POR_CONSULTA) < salida ? sumarDiasISO(desde, NOCHES_POR_CONSULTA) : salida
    const tramo = await consultarDisponibilidad(id, desde, hasta, excluirReserva)
    if (tramo.noches.some((n) => !n.libre)) return false
  }
  return true
}

export async function consultarAlojamiento(): Promise<AlojamientoResponse> {
  const { data } = await cliente.get<AlojamientoResponse>('/api/alojamiento')
  return data
}

export async function listarTemporadas(pagina = 0): Promise<Pagina<TemporadaResponse>> {
  const { data } = await cliente.get<Pagina<TemporadaResponse>>('/api/temporadas', { params: { pagina } })
  return data
}

export async function consultarTemporada(id: string): Promise<TemporadaResponse> {
  const { data } = await cliente.get<TemporadaResponse>(`/api/temporadas/${encodeURIComponent(id)}`)
  return data
}

export async function consultarPoliticaVigente(): Promise<PoliticaCancelacionResponse> {
  const { data } = await cliente.get<PoliticaCancelacionResponse>('/api/politica-cancelacion')
  return data
}

export async function cotizarEstancia(solicitud: CotizarEstanciaRequest): Promise<CotizacionResponse> {
  const { data } = await cliente.post<CotizacionResponse>('/api/cotizaciones', solicitud)
  return data
}

/** Quita los filtros vacíos para no enviarlos como parámetros */
function limpiar(filtros: FiltrosDisponibles): Record<string, unknown> {
  return Object.fromEntries(
    Object.entries(filtros).filter(
      ([, valor]) => valor !== undefined && valor !== null && valor !== '' && !(Array.isArray(valor) && valor.length === 0),
    ),
  )
}

// ---------- Llaves de React Query ----------

export const llavesPublicas = {
  apartamentos: (pagina: number) => ['apartamentos', 'lista', pagina] as const,
  disponibles: (filtros: FiltrosDisponibles, pagina: number) => ['apartamentos', 'disponibles', filtros, pagina] as const,
  apartamento: (id: string) => ['apartamentos', 'detalle', id] as const,
  disponibilidad: (id: string, desde: string, hasta: string, excluirReserva = '') =>
    ['apartamentos', 'disponibilidad', id, desde, hasta, excluirReserva] as const,
  estanciaLibre: (id: string, entrada: string, salida: string, excluirReserva = '') =>
    ['apartamentos', 'estancia-libre', id, entrada, salida, excluirReserva] as const,
  alojamiento: ['alojamiento'] as const,
  temporadas: (pagina: number) => ['temporadas', 'lista', pagina] as const,
  temporada: (id: string) => ['temporadas', 'detalle', id] as const,
  politicaVigente: ['politica-cancelacion', 'vigente'] as const,
  mediosPago: ['medios-pago', 'activos'] as const,
  opcionesFiltro: ['apartamentos', 'filtros'] as const,
}

/** GET /api/apartamentos/filtros: opciones reales de los filtros "Dormitorios", "Características" y "Servicios" */
export interface OpcionesFiltroResponse {
  /** Números de dormitorios de los apartamentos activos */
  dormitorios: number[]
  /** Rasgos de los apartamentos activos, por ejemplo "Vista al mar" */
  caracteristicas: string[]
  /** Elementos de la dotación de los apartamentos activos */
  servicios: string[]
}

export function useOpcionesFiltro() {
  return useQuery({
    queryKey: llavesPublicas.opcionesFiltro,
    queryFn: async () => (await cliente.get<OpcionesFiltroResponse>('/api/apartamentos/filtros')).data,
    staleTime: 5 * 60 * 1000,
  })
}

/** GET /api/medios-pago: medios aceptados y activos (L-18), públicos */
export async function listarMediosPago(): Promise<MedioPagoResponse[]> {
  const { data } = await cliente.get<MedioPagoResponse[]>('/api/medios-pago')
  return data
}

export function useMediosPago() {
  return useQuery({ queryKey: llavesPublicas.mediosPago, queryFn: listarMediosPago, staleTime: 5 * 60 * 1000 })
}

// ---------- Hooks ----------

export function useApartamentos(pagina = 0) {
  return useQuery({
    queryKey: llavesPublicas.apartamentos(pagina),
    queryFn: () => listarApartamentos(pagina),
    placeholderData: keepPreviousData,
  })
}

/** Busca solo cuando hay filtros (entrada, salida y ocupantes) */
export function useApartamentosDisponibles(filtros: FiltrosDisponibles | null, pagina = 0) {
  return useQuery({
    queryKey: llavesPublicas.disponibles(filtros ?? ({} as FiltrosDisponibles), pagina),
    queryFn: () => buscarDisponibles(filtros as FiltrosDisponibles, pagina),
    enabled: !!filtros?.entrada && !!filtros?.salida && !!filtros?.ocupantes,
    placeholderData: keepPreviousData,
  })
}

export function useApartamento(id: string | undefined) {
  return useQuery({
    queryKey: llavesPublicas.apartamento(id ?? ''),
    queryFn: () => consultarApartamento(id as string),
    enabled: !!id,
  })
}

export function useDisponibilidadApartamento(
  id: string | undefined,
  desde: string,
  hasta: string,
  excluirReserva?: string,
) {
  return useQuery({
    queryKey: llavesPublicas.disponibilidad(id ?? '', desde, hasta, excluirReserva),
    queryFn: () => consultarDisponibilidad(id as string, desde, hasta, excluirReserva),
    enabled: !!id && !!desde && !!hasta && desde < hasta,
    placeholderData: keepPreviousData,
  })
}

/**
 * Para cada apartamento, si tiene libres todas las noches de [entrada, salida): true, false o undefined mientras
 * consulta (o si aún no hay fechas). Lo usan los selectores de apartamento para marcar los "No disponible".
 */
export function useEstanciasLibres(
  ids: string[],
  entrada: string,
  salida: string,
  excluirReserva?: string,
): Record<string, boolean | undefined> {
  const conFechas = !!entrada && !!salida && entrada < salida
  const consultas = useQueries({
    queries: ids.map((id) => ({
      queryKey: llavesPublicas.estanciaLibre(id, entrada, salida, excluirReserva),
      queryFn: () => estanciaLibre(id, entrada, salida, excluirReserva),
      enabled: conFechas,
      staleTime: 30_000,
    })),
  })
  return Object.fromEntries(ids.map((id, i) => [id, conFechas ? consultas[i]?.data : undefined]))
}

export function useAlojamiento() {
  return useQuery({
    queryKey: llavesPublicas.alojamiento,
    queryFn: consultarAlojamiento,
    staleTime: 5 * 60 * 1000,
  })
}

export function useTemporadas(pagina = 0) {
  return useQuery({
    queryKey: llavesPublicas.temporadas(pagina),
    queryFn: () => listarTemporadas(pagina),
    placeholderData: keepPreviousData,
  })
}

export function useTemporada(id: string | undefined) {
  return useQuery({
    queryKey: llavesPublicas.temporada(id ?? ''),
    queryFn: () => consultarTemporada(id as string),
    enabled: !!id,
  })
}

export function usePoliticaVigente() {
  return useQuery({
    queryKey: llavesPublicas.politicaVigente,
    queryFn: consultarPoliticaVigente,
    staleTime: 5 * 60 * 1000,
  })
}

/** Cotizar no cambia nada en el servidor, pero se dispara a pedido: por eso es una mutación */
export function useCotizar() {
  return useMutation({ mutationFn: cotizarEstancia })
}

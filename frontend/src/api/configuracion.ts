import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { cliente } from './cliente'
import { llavesPublicas } from './publico'
import type {
  AlojamientoResponse,
  FechaISO,
  Pagina,
  PoliticaCancelacionResponse,
  ServicioAdicionalResponse,
  TemporadaResponse,
  TipoTemporada,
} from './tipos'

/*
 * Configuración del alojamiento (solo ADMINISTRADOR): datos del alojamiento, temporadas y política de cancelación.
 * Las consultas públicas (alojamiento, temporadas, política vigente) están en publico.ts; aquí van los cambios
 * y el historial de versiones de la política, que solo ve el administrador.
 */

// ---------- Tipos (docs/api/openapi.yaml) ----------

export interface ActualizarAlojamientoRequest {
  nombre: string
  descripcion: string | null
  ciudad: string
  direccion: string | null
  latitud: number
  longitud: number
  normaConvivencia: string | null
  /** "15:00"; opcional, pero si llega una debe llegar la otra (L-12) */
  horaEntrada: string | null
  horaSalida: string | null
}

/** Fecha final excluida (nota de A.3) */
export interface RangoFechasRequest {
  fechaInicio: FechaISO
  fechaFin: FechaISO
}

export interface TarifaTemporadaRequest {
  identificacionApartamento: string
  valorPorOcupante: number
}

export interface CrearTemporadaRequest {
  id: string
  nombre: string
  tipo: TipoTemporada
  /** Al menos uno si no es BASE; la base no lleva rangos */
  rangos: RangoFechasRequest[]
  /** Una por cada apartamento activo (7.4) */
  tarifas: TarifaTemporadaRequest[]
}

export interface TramoCancelacionRequest {
  antelacionMinimaDias: number
  porcentajeRetencion: number
}

export interface ActualizarPoliticaCancelacionRequest {
  consecuenciaNoShow: string
  tramos: TramoCancelacionRequest[]
}

// ---------- Funciones ----------

export async function actualizarAlojamiento(datos: ActualizarAlojamientoRequest): Promise<AlojamientoResponse> {
  const { data } = await cliente.put<AlojamientoResponse>('/api/alojamiento', datos)
  return data
}

// ---------- Servicios adicionales ----------

export interface ServicioAdicionalRequest {
  nombre: string
  descripcion: string | null
  generaCargo: boolean
  /** Pesos enteros; se ignora si no genera cargo */
  valor: number | null
}

export interface RegistrarServicioAdicionalRequest extends ServicioAdicionalRequest {
  /** Letras, números y guiones, por ejemplo SRV-SPA */
  id: string
}

export async function crearServicioAdicional(datos: RegistrarServicioAdicionalRequest): Promise<ServicioAdicionalResponse> {
  const { data } = await cliente.post<ServicioAdicionalResponse>('/api/alojamiento/servicios', datos)
  return data
}

export async function actualizarServicioAdicional(
  id: string,
  datos: ServicioAdicionalRequest,
): Promise<ServicioAdicionalResponse> {
  const { data } = await cliente.put<ServicioAdicionalResponse>(
    `/api/alojamiento/servicios/${encodeURIComponent(id)}`,
    datos,
  )
  return data
}

export async function cambiarEstadoServicioAdicional(
  id: string,
  activar: boolean,
): Promise<ServicioAdicionalResponse> {
  const { data } = await cliente.put<ServicioAdicionalResponse>(
    `/api/alojamiento/servicios/${encodeURIComponent(id)}/${activar ? 'activar' : 'desactivar'}`,
  )
  return data
}

export async function crearTemporada(datos: CrearTemporadaRequest): Promise<TemporadaResponse> {
  const { data } = await cliente.post<TemporadaResponse>('/api/temporadas', datos)
  return data
}

export async function agregarRangoTemporada(id: string, rango: RangoFechasRequest): Promise<TemporadaResponse> {
  const { data } = await cliente.post<TemporadaResponse>(`/api/temporadas/${encodeURIComponent(id)}/rangos`, rango)
  return data
}

export async function actualizarPoliticaCancelacion(
  datos: ActualizarPoliticaCancelacionRequest,
): Promise<PoliticaCancelacionResponse> {
  const { data } = await cliente.put<PoliticaCancelacionResponse>('/api/politica-cancelacion', datos)
  return data
}

/** De la más reciente a la más antigua, 10 por página */
export async function listarVersionesPolitica(pagina = 0): Promise<Pagina<PoliticaCancelacionResponse>> {
  const { data } = await cliente.get<Pagina<PoliticaCancelacionResponse>>('/api/politica-cancelacion/versiones', {
    params: { pagina },
  })
  return data
}

// ---------- Llaves de React Query ----------

export const llavesConfiguracion = {
  versionesPolitica: (pagina: number) => ['politica-cancelacion', 'versiones', pagina] as const,
}

// ---------- Consultas ----------

export function useVersionesPolitica(pagina = 0) {
  return useQuery({
    queryKey: llavesConfiguracion.versionesPolitica(pagina),
    queryFn: () => listarVersionesPolitica(pagina),
    placeholderData: keepPreviousData,
  })
}

// ---------- Mutaciones ----------

export function useActualizarAlojamiento() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: actualizarAlojamiento,
    onSuccess: (alojamiento) => {
      queryClient.setQueryData(llavesPublicas.alojamiento, alojamiento)
      return queryClient.invalidateQueries({ queryKey: llavesPublicas.alojamiento })
    },
  })
}

// ---------- Parámetros del alojamiento ----------

/** PUT /api/alojamiento/parametros: la configuración completa; el dinero en pesos enteros, las horas "HH:mm" */
export interface ActualizarParametrosRequest {
  umbralEdadFacturable: number
  tiempoPreparacionHoras: number
  plazoConfirmacionHoras: number
  horaLimiteNoShow: string
  horaInicioLlegadaNocturna: string
  recargoRecepcionNocturna: number
  estanciaMinimaNoches: number
  estanciaMinimaActiva: boolean
  exigeAnticipo: boolean
  porcentajeAnticipo: number
  conceptoDepositoGarantia: string
  depositoGarantia: number
  aceptaMascotas: boolean
  cargoAseoMascota: number
  maximoMascotas: number
  pesoMaximoMascotaKg: number
  edadMinimaCuenta: number
}

export async function actualizarParametros(datos: ActualizarParametrosRequest): Promise<AlojamientoResponse> {
  const { data } = await cliente.put<AlojamientoResponse>('/api/alojamiento/parametros', datos)
  return data
}

/**
 * Rigen de inmediato: se reemplaza el alojamiento en caché y se descartan las cotizaciones guardadas, que se
 * calcularon con los parámetros anteriores.
 */
export function useActualizarParametros() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: actualizarParametros,
    onSuccess: (alojamiento) => {
      queryClient.setQueryData(llavesPublicas.alojamiento, alojamiento)
      queryClient.removeQueries({ queryKey: ['cotizacion'] })
      return queryClient.invalidateQueries({ queryKey: llavesPublicas.alojamiento })
    },
  })
}

/** Los servicios viajan dentro del alojamiento: al cambiar uno se vuelve a consultar */
function useRefrescarAlojamiento() {
  const queryClient = useQueryClient()
  return () => queryClient.invalidateQueries({ queryKey: llavesPublicas.alojamiento })
}

export function useCrearServicioAdicional() {
  const refrescar = useRefrescarAlojamiento()
  return useMutation({ mutationFn: crearServicioAdicional, onSuccess: refrescar })
}

export function useActualizarServicioAdicional() {
  const refrescar = useRefrescarAlojamiento()
  return useMutation({
    mutationFn: ({ id, datos }: { id: string; datos: ServicioAdicionalRequest }) => actualizarServicioAdicional(id, datos),
    onSuccess: refrescar,
  })
}

export function useCambiarEstadoServicioAdicional() {
  const refrescar = useRefrescarAlojamiento()
  return useMutation({
    mutationFn: ({ id, activar }: { id: string; activar: boolean }) => cambiarEstadoServicioAdicional(id, activar),
    onSuccess: refrescar,
  })
}

/** Una temporada nueva cambia las temporadas, las tarifas de los apartamentos y las cotizaciones */
function useRefrescarTemporadas() {
  const queryClient = useQueryClient()
  return () =>
    Promise.all([
      queryClient.invalidateQueries({ queryKey: ['temporadas'] }),
      queryClient.invalidateQueries({ queryKey: ['apartamentos'] }),
    ])
}

export function useCrearTemporada() {
  const refrescar = useRefrescarTemporadas()
  return useMutation({ mutationFn: crearTemporada, onSuccess: refrescar })
}

export function useAgregarRangoTemporada() {
  const refrescar = useRefrescarTemporadas()
  return useMutation({
    mutationFn: ({ id, rango }: { id: string; rango: RangoFechasRequest }) => agregarRangoTemporada(id, rango),
    onSuccess: refrescar,
  })
}

export function useActualizarPoliticaCancelacion() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: actualizarPoliticaCancelacion,
    onSuccess: (politica) => {
      queryClient.setQueryData(llavesPublicas.politicaVigente, politica)
      return queryClient.invalidateQueries({ queryKey: ['politica-cancelacion'] })
    },
  })
}

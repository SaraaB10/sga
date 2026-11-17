import { addDays, format, parseISO, subDays, subYears } from 'date-fns'
import type { FechaISO, OcupanteRequest } from '@/api/tipos'
import { esFechaValida, esMenorNoPaga } from './reglas'

/** Mismo mensaje que responde la API cuando el grupo no tiene ningún ocupante que pague noche */
export const MENSAJE_SIN_ADULTOS =
  'El grupo debe incluir al menos un adulto que pague noche; el titular siempre es facturable'

/**
 * Separa el grupo en adultos (pagan noche; '' cuenta como adulto) y menores del umbral (no pagan), conservando
 * las fechas de cada uno.
 */
export function separarGrupo(
  huespedes: string[],
  umbral: number | null,
  referencia?: string,
): { adultos: string[]; menores: string[] } {
  const menores = huespedes.filter((h) => esMenorNoPaga(h, umbral, referencia))
  const adultos = huespedes.filter((h) => !esMenorNoPaga(h, umbral, referencia))
  return { adultos, menores }
}

/** El grupo no tiene ningún adulto que pague noche: no se puede cotizar ni reservar */
export function grupoSinAdultos(huespedes: string[], umbral: number | null, referencia?: string): boolean {
  return separarGrupo(huespedes, umbral, referencia).adultos.length === 0
}

/** "2 adultos · 1 menor" */
export function resumenHuespedes(huespedes: string[], umbral: number | null, referencia?: string): string {
  const { adultos, menores } = separarGrupo(huespedes, umbral, referencia)
  const textoAdultos = `${adultos.length} ${adultos.length === 1 ? 'adulto' : 'adultos'}`
  return menores.length > 0
    ? `${textoAdultos} · ${menores.length} ${menores.length === 1 ? 'menor' : 'menores'}`
    : textoAdultos
}

/*
 * La estancia que se busca viaja entre páginas en la URL:
 *   ?entrada=2026-12-12&salida=2026-12-14&h=1990-05-20&h=2021-03-02
 * Cada "h" es un huésped con su fecha de nacimiento (puede venir vacía: se cuenta como adulto).
 */

export interface Estancia {
  entrada: string
  salida: string
  /** Fecha de nacimiento de cada huésped; '' si no se indicó */
  huespedes: string[]
}

export function leerEstancia(params: URLSearchParams): Estancia {
  const huespedes = params.getAll('h')
  return {
    entrada: params.get('entrada') ?? '',
    salida: params.get('salida') ?? '',
    huespedes: huespedes.length > 0 ? huespedes : [''],
  }
}

export function estanciaAParams(estancia: Estancia, extra: Record<string, string | undefined> = {}): URLSearchParams {
  const params = new URLSearchParams()
  if (estancia.entrada) params.set('entrada', estancia.entrada)
  if (estancia.salida) params.set('salida', estancia.salida)
  estancia.huespedes.forEach((h) => params.append('h', h))
  Object.entries(extra).forEach(([clave, valor]) => {
    if (valor) params.set(clave, valor)
  })
  return params
}

/**
 * Fecha que representa a alguien que paga noche cuando el huésped no indicó su fecha de nacimiento:
 * cumple el umbral de edad el día anterior a la llegada. Sin umbral configurado nadie queda exento,
 * así que basta con la fecha de llegada.
 */
export function fechaQuePaga(umbral: number | null, referencia?: string): FechaISO {
  const base = esFechaValida(referencia) ? parseISO(referencia) : new Date()
  return format(umbral !== null ? subDays(subYears(base, umbral), 1) : base, 'yyyy-MM-dd')
}

/** Solo se envían las fechas de nacimiento si todos los huéspedes la tienen */
export function fechasCompletas(huespedes: string[]): FechaISO[] | undefined {
  return huespedes.length > 0 && huespedes.every(esFechaValida) ? huespedes : undefined
}

/** Ocupantes para cotizar: sin fecha, el menor cuenta como menor y los demás como adultos (pagan noche) */
export function ocupantesParaCotizar(
  huespedes: { nombre?: string; fechaNacimiento: string; tipo?: TipoHuesped | '' }[],
  umbral: number | null,
  referencia?: string,
): OcupanteRequest[] {
  const adulto = fechaQuePaga(umbral, referencia)
  const menor = fechaQueNoPaga(umbral, referencia)
  return huespedes.map((h, i) => ({
    nombre: h.nombre?.trim() || `Huésped ${i + 1}`,
    fechaNacimiento: esFechaValida(h.fechaNacimiento)
      ? h.fechaNacimiento
      : h.tipo === 'menor' && menor
        ? menor
        : adulto,
  }))
}

// ---------- Tipo de cada huésped ----------

/** "paga": tiene el umbral de edad o más a la fecha de entrada; "menor": aún no lo cumple */
export type TipoHuesped = 'paga' | 'menor'

/** Nombre del tipo: "Paga noche" o "Menor de 6" con el umbral del alojamiento */
export function nombreTipo(tipo: TipoHuesped, umbral: number | null): string {
  return tipo === 'paga' ? 'Paga noche' : umbral !== null ? `Menor de ${umbral}` : 'Menor'
}

/** Tipo según la fecha de nacimiento; sin fecha válida, el huésped cuenta como adulto */
export function tipoDeFecha(fecha: string, umbral: number | null, referencia?: string): TipoHuesped {
  return esMenorNoPaga(fecha, umbral, referencia) ? 'menor' : 'paga'
}

const aISO = (fecha: Date): FechaISO => format(fecha, 'yyyy-MM-dd')
const menorFecha = (a: FechaISO, b: FechaISO): FechaISO => (a < b ? a : b)

/**
 * Fechas de nacimiento que admite cada tipo, con el umbral y la fecha de entrada (o hoy, si no hay entrada).
 * Paga noche: nació a más tardar el día en que cumple el umbral a la entrada. Menor: nació después de ese día
 * y no después de la entrada. Ninguna fecha puede ser futura. Sin umbral no hay menores y no se limita el tipo.
 */
export function rangoNacimiento(
  tipo: TipoHuesped,
  umbral: number | null,
  referencia?: string,
): { min?: FechaISO; max: FechaISO } {
  const hoy = aISO(new Date())
  if (umbral === null) return { max: hoy }
  const base = esFechaValida(referencia) ? parseISO(referencia) : new Date()
  const cumpleUmbral = aISO(subYears(base, umbral))
  if (tipo === 'paga') return { max: menorFecha(cumpleUmbral, hoy) }
  return { min: aISO(addDays(parseISO(cumpleUmbral), 1)), max: menorFecha(aISO(base), hoy) }
}

/** La fecha cae dentro del rango de su tipo */
export function fechaCuadraConTipo(
  fecha: string,
  tipo: TipoHuesped,
  umbral: number | null,
  referencia?: string,
): boolean {
  if (!esFechaValida(fecha)) return false
  const { min, max } = rangoNacimiento(tipo, umbral, referencia)
  return fecha <= max && (min === undefined || fecha >= min)
}

/**
 * Fecha que representa a un menor del umbral que aún no indicó su fecha de nacimiento: el día después de la
 * última fecha que paga noche. null si no hay umbral o si esa fecha sería futura.
 */
export function fechaQueNoPaga(umbral: number | null, referencia?: string): FechaISO | null {
  const { min, max } = rangoNacimiento('menor', umbral, referencia)
  return min !== undefined && min <= max ? min : null
}

/** Cuántos huéspedes de cada tipo hay en el grupo */
export function contarTipos(tipos: (TipoHuesped | '')[]): { paga: number; menor: number } {
  return {
    paga: tipos.filter((t) => t === 'paga').length,
    menor: tipos.filter((t) => t === 'menor').length,
  }
}

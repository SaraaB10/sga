import type { CargoResponse, FolioResponse, MedioPago, TipoCargo } from '@/api/reservas'

export const NOMBRE_TIPO_CARGO: Record<TipoCargo, string> = {
  ALOJAMIENTO: 'Alojamiento',
  SERVICIO_ADICIONAL: 'Servicio adicional',
  AJUSTE_MODIFICACION: 'Ajuste',
  PENALIDAD_CANCELACION_NOSHOW: 'Penalidad',
}

/** devolucion: el inverso del cargo del depósito y el dinero devuelto al huésped */
export type ClaseMovimiento = 'cargo' | 'correccion' | 'pago' | 'devolucion'

export interface Movimiento {
  clave: string
  /** "C1", "C2"... para los cargos y correcciones; "P1"... para los pagos */
  numero: string
  clase: ClaseMovimiento
  fecha: string
  tipo: string
  concepto: string
  /** Efecto en el saldo: los pagos restan, las correcciones tienen el signo opuesto al cargo */
  valor: number
  /** Solo cargos y correcciones: posición en la lista cargos del folio (para POST /correcciones) */
  indiceCargo?: number
  /** Corrección: número del cargo que corrige ("C3"); vacío si no se encontró el cargo */
  corrige?: string
  /** Cargo: número de la corrección que lo revirtió ("C5") */
  corregidoPor?: string
  /** Corrección: el motivo, sin el prefijo "Reverso de '...'" */
  motivo?: string
}

const numeroCargo = (indice: number) => `C${indice + 1}`

/** Prefijo con el que el backend asienta toda corrección (Cargo.inverso) */
const PREFIJO_REVERSO = "Reverso de '"

/** Prefijo con el que el backend asienta el inverso del depósito (DepositoGarantia.generarDevolucion) */
const PREFIJO_DEVOLUCION = 'Devolución de '

const esReverso = (cargo: CargoResponse) => cargo.concepto.startsWith(PREFIJO_REVERSO)
const esDevolucion = (cargo: CargoResponse) => cargo.valor < 0 && cargo.concepto.startsWith(PREFIJO_DEVOLUCION)

/**
 * El backend asienta la corrección como un cargo del mismo tipo, valor opuesto y concepto
 * "Reverso de '<concepto>': <motivo>" (Cargo.inverso), y la devolución del depósito como "Devolución de <concepto>".
 * La respuesta no trae el enlace, así que se reconstruye: cada corrección se asocia al primer cargo anterior que
 * encaja y que todavía no estaba corregido.
 */
export function enlazarCorrecciones(cargos: CargoResponse[]): Map<number, number> {
  const corregidoPor = new Map<number, number>() // cargo original → corrección
  cargos.forEach((cargo, i) => {
    if (!esReverso(cargo) && !esDevolucion(cargo)) return
    for (let j = 0; j < i; j++) {
      const original = cargos[j]
      const encaja = esReverso(cargo)
        ? cargo.concepto.startsWith(`Reverso de '${original.concepto}': `)
        : cargo.concepto === `${PREFIJO_DEVOLUCION}${original.concepto}`
      if (
        !corregidoPor.has(j) &&
        ![...corregidoPor.values()].includes(j) &&
        original.tipo === cargo.tipo &&
        original.valor === -cargo.valor &&
        encaja
      ) {
        corregidoPor.set(j, i)
        return
      }
    }
  })
  return corregidoPor
}

/** Cargos, correcciones y pagos en una sola lista, por fecha (y en el orden en que se asentaron) */
/** @param nombreMedio nombre de cada medio de pago por su código (useNombreMedio) */
export function movimientosDe(folio: FolioResponse, nombreMedio: (codigo: MedioPago) => string): Movimiento[] {
  const enlaces = enlazarCorrecciones(folio.cargos)
  const corrigeA = new Map([...enlaces].map(([original, correccion]) => [correccion, original]))

  const cargos: Movimiento[] = folio.cargos.map((c, i) => {
    const original = corrigeA.get(i)
    const correccion = enlaces.get(i)
    // Una corrección cuyo cargo no se encuentra se muestra igual, con su concepto completo y sin enlace
    const esCorreccion = esReverso(c)
    const esDevolucionDeposito = !esCorreccion && esDevolucion(c)
    const enlazada = original !== undefined
    return {
      clave: `c-${i}`,
      numero: numeroCargo(i),
      clase: esCorreccion ? 'correccion' : esDevolucionDeposito ? 'devolucion' : 'cargo',
      fecha: c.fecha,
      tipo: esCorreccion ? 'Corrección' : esDevolucionDeposito ? 'Devolución' : NOMBRE_TIPO_CARGO[c.tipo],
      concepto: enlazada ? folio.cargos[original].concepto : c.concepto,
      valor: c.valor,
      indiceCargo: i,
      corrige: enlazada ? numeroCargo(original) : undefined,
      corregidoPor: correccion !== undefined ? numeroCargo(correccion) : undefined,
      motivo: esDevolucionDeposito
        ? 'Se anula el cargo del depósito'
        : enlazada
          ? c.concepto.slice(`${PREFIJO_REVERSO}${folio.cargos[original].concepto}': `.length)
          : undefined,
    }
  })

  // Las devoluciones al huésped suman al saldo (es dinero que sale); los abonos restan
  const pagos: Movimiento[] = folio.pagos.map((p, i) => ({
    clave: `p-${i}`,
    numero: `P${i + 1}`,
    clase: p.devolucion ? 'devolucion' : 'pago',
    fecha: p.fecha,
    tipo: p.devolucion ? 'Devolución' : 'Pago',
    concepto: p.devolucion ? `Devuelto al huésped · ${nombreMedio(p.medio)}` : nombreMedio(p.medio),
    valor: p.devolucion ? p.valor : -p.valor,
  }))

  // sort es estable: dentro del mismo día se conserva el orden de asiento, primero cargos y luego pagos
  return [...cargos, ...pagos].sort((a, b) => a.fecha.localeCompare(b.fecha))
}

/** Cargos que se pueden corregir: ni son correcciones ni ya fueron corregidos */
export function cargosCorregibles(movimientos: Movimiento[]): Movimiento[] {
  return movimientos
    .filter((m) => m.clase === 'cargo' && !m.corregidoPor)
    .sort((a, b) => (a.indiceCargo ?? 0) - (b.indiceCargo ?? 0))
}

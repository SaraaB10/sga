import { useTodosLosMediosPago } from '@/api/configuracion'
import { useMediosPago } from '@/api/publico'
import type { MedioPagoResponse } from '@/api/reservas'
import { useAuth } from '@/auth/AuthContext'

/*
 * Nombres de los medios de pago (L-18). Salen del catálogo del backend: el administrador agrega, activa y desactiva
 * medios, así que aquí no hay una lista fija. Un pago antiguo con un medio que ya no está activo se muestra con su
 * código legible (EFECTIVO_EN_PESOS → "Efectivo en pesos").
 */

/** "EFECTIVO_EN_PESOS" → "Efectivo en pesos" */
export function codigoLegible(codigo: string): string {
  const texto = codigo.replace(/_/g, ' ').toLowerCase()
  return texto.charAt(0).toUpperCase() + texto.slice(1)
}

export function nombreDeMedio(codigo: string, catalogo: MedioPagoResponse[] | undefined): string {
  return catalogo?.find((m) => m.codigo === codigo)?.nombre ?? codigoLegible(codigo)
}

/**
 * Función que da el nombre de un medio por su código. El administrador consulta el catálogo completo (también los
 * inactivos); los demás, los activos.
 */
export function useNombreMedio(): (codigo: string) => string {
  const { roles } = useAuth()
  const esAdmin = roles.includes('ADMINISTRADOR')
  const activos = useMediosPago()
  const todos = useTodosLosMediosPago(esAdmin)
  const catalogo = esAdmin ? (todos.data ?? activos.data) : activos.data
  return (codigo: string) => nombreDeMedio(codigo, catalogo)
}

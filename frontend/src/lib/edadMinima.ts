import { format, subYears } from 'date-fns'

/*
 * Edad mínima para crear una cuenta. El backend la toma de sga.usuarios.edad-minima y tiene la última palabra:
 * si no se cumple responde 400 sobre fechaNacimiento con este mismo mensaje.
 */
export const EDAD_MINIMA_CUENTA = 18

export const MENSAJE_EDAD_MINIMA = `Debes tener al menos ${EDAD_MINIMA_CUENTA} años para crear una cuenta`

/** Última fecha de nacimiento con la que hoy se cumple la edad mínima ("yyyy-MM-dd") */
export function fechaMaximaNacimientoCuenta(): string {
  return format(subYears(new Date(), EDAD_MINIMA_CUENTA), 'yyyy-MM-dd')
}

/** La fecha ("yyyy-MM-dd") da la edad mínima o más hoy */
export function cumpleEdadMinima(fecha: string): boolean {
  return fecha <= fechaMaximaNacimientoCuenta()
}

/** Fecha completa del campo de fecha (año de 4 cifras): mientras se escribe el año no se marca el error */
export function esFechaCompleta(fecha: string): boolean {
  return /^\d{4}-\d{2}-\d{2}$/.test(fecha) && Number(fecha.slice(0, 4)) >= 1000
}

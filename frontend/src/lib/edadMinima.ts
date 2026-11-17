import { format, subYears } from 'date-fns'

/*
 * Edad mínima para crear una cuenta. Es un parámetro del alojamiento (GET /api/alojamiento, parametros.edadMinimaCuenta)
 * que edita el administrador. El backend tiene la última palabra: si no se cumple responde 400 sobre
 * fechaNacimiento con este mismo mensaje. Mientras el alojamiento no carga (edad null) no se marca nada aquí.
 */

export function mensajeEdadMinima(edad: number | null): string {
  return edad === null
    ? 'No tienes la edad mínima para crear una cuenta'
    : `Debes tener al menos ${edad} años para crear una cuenta`
}

/** Última fecha de nacimiento con la que hoy se cumple la edad mínima ("yyyy-MM-dd"); undefined sin el dato */
export function fechaMaximaNacimientoCuenta(edad: number | null): string | undefined {
  return edad === null ? undefined : format(subYears(new Date(), edad), 'yyyy-MM-dd')
}

/** La fecha ("yyyy-MM-dd") da la edad mínima o más hoy; sin el dato no se rechaza (decide el backend) */
export function cumpleEdadMinima(fecha: string, edad: number | null): boolean {
  const maxima = fechaMaximaNacimientoCuenta(edad)
  return maxima === undefined || fecha <= maxima
}

/** Fecha completa del campo de fecha (año de 4 cifras): mientras se escribe el año no se marca el error */
export function esFechaCompleta(fecha: string): boolean {
  return /^\d{4}-\d{2}-\d{2}$/.test(fecha) && Number(fecha.slice(0, 4)) >= 1000
}

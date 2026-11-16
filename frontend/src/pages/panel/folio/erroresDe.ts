import { erroresDelServidor, type Errores } from '@/lib/validacion'

/** Errores por campo de un 400 de validación (vacío si el error no es un 400 de la API) */
export function erroresDe(error: unknown): Errores {
  return erroresDelServidor(error)
}

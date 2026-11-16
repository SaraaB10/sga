import { useState } from 'react'
import { format, subYears } from 'date-fns'
import { z } from 'zod'
import { esErrorApi } from '@/api/cliente'

/*
 * Piezas de los esquemas zod de los formularios. Repiten las reglas de los DTO del backend (obligatorios,
 * longitudes, formatos y números) para avisar junto al campo antes de enviar; el backend siempre tiene la última
 * palabra y sus 400 por campo se llevan al mismo campo con erroresDelServidor.
 */

/** Campo → mensaje del primer error de ese campo */
export type Errores = Record<string, string | undefined>

// ---------- Formatos ----------

/** Al menos una mayúscula y un número, como el @Pattern de las contraseñas del backend */
const PATRON_CONTRASENA = /^(?=.*\p{Lu})(?=.*\d).*$/u
/** Teléfono: exactamente 10 dígitos, sin espacios ni símbolos. Mismo patrón y mensaje de ReglaTelefono del backend */
export const DIGITOS_TELEFONO = 10
const PATRON_TELEFONO = /^\d{10}$/
export const MENSAJE_TELEFONO = 'El teléfono debe tener 10 dígitos, solo números'
/** Documento: de 6 a 10 dígitos, sin espacios ni símbolos. Mismo patrón y mensaje de ReglaDocumento del backend */
export const MAXIMO_DIGITOS_DOCUMENTO = 10
const PATRON_DOCUMENTO = /^\d{6,10}$/
export const MENSAJE_DOCUMENTO = 'El documento debe tener entre 6 y 10 dígitos, solo números'
/** URL de una imagen: https:// y la dirección, sin espacios. Mismo patrón y mensaje del backend (ReglaTexto) */
const PATRON_URL = /^https:\/\/\S+$/
export const MENSAJE_URL_IMAGEN = 'La URL de la imagen debe empezar por https:// y no tener espacios'
/** Correo con usuario, dominio y extensión, como ReglaTexto.PATRON_CORREO del backend */
const PATRON_CORREO = /^[\w.+-]+@[\w-]+(\.[\w-]+)+$/
/**
 * Nombre de una persona: solo letras (con tildes y ñ) y espacios, de 3 a 80 caracteres, sin espacios al inicio ni al
 * final. Mismo patrón y mensaje de ReglaNombre del backend
 */
const PATRON_NOMBRE = /^(?=.{3,80}$)\p{L}(?:[\p{L} ]*\p{L})?$/u
export const MENSAJE_NOMBRE =
  'El nombre debe tener entre 3 y 80 caracteres, solo letras y espacios, sin espacios al inicio ni al final'
export const MAXIMO_NOMBRE_PERSONA = 80
/** Edad máxima que se admite en una fecha de nacimiento, como @FechaNacimientoRazonable del backend */
export const ANIOS_MAXIMOS_NACIMIENTO = 110
export const MENSAJE_NACIMIENTO_ANTIGUO = `La fecha de nacimiento no puede ser de hace más de ${ANIOS_MAXIMOS_NACIMIENTO} años`
/** Largos máximos de los textos libres, los mismos del backend (ReglaTexto) */
export const MAXIMO_MOTIVO = 250
export const MAXIMO_DESCRIPCION = 1000
const PATRON_FECHA = /^\d{4}-\d{2}-\d{2}$/

export const AYUDA_CONTRASENA = 'Mínimo 8 caracteres, con al menos una mayúscula y un número.'

const hoy = () => format(new Date(), 'yyyy-MM-dd')

/** "yyyy-MM-dd" que corresponde a un día real */
export function esFechaISO(valor: string): boolean {
  if (!PATRON_FECHA.test(valor)) return false
  const fecha = new Date(`${valor}T00:00:00`)
  return !Number.isNaN(fecha.getTime()) && format(fecha, 'yyyy-MM-dd') === valor
}

/** Valor escrito en pesos ("320.000", "$320000" o "320000") a número; NaN si no es válido */
export function aPesos(texto: string): number {
  const limpio = texto.replace(/[$.\s]/g, '')
  return limpio === '' ? NaN : Number(limpio)
}

// ---------- Campos de texto ----------

/** Texto obligatorio, sin espacios a los lados, con longitud máxima opcional */
export function textoObligatorio(mensaje: string, maximo?: number, mensajeMaximo?: string) {
  const base = z.string().trim().min(1, mensaje)
  return maximo === undefined ? base : base.max(maximo, mensajeMaximo ?? `Admite máximo ${maximo} caracteres`)
}

/** Texto opcional con longitud máxima */
export function textoOpcional(maximo: number, mensajeMaximo?: string) {
  return z
    .string()
    .trim()
    .max(maximo, mensajeMaximo ?? `Admite máximo ${maximo} caracteres`)
}

export function correo(mensajeVacio = 'Escribe el correo', maximo = 120) {
  return z
    .string()
    .trim()
    .min(1, mensajeVacio)
    .regex(PATRON_CORREO, 'El correo no tiene un formato válido')
    .email('El correo no tiene un formato válido')
    .max(maximo, `El correo admite máximo ${maximo} caracteres`)
}

/**
 * Nombre de una persona (registro, perfil, usuarios, titular, acompañantes, ocupantes, autor de una novedad y quien
 * autoriza un cierre). Sin trim: un espacio al inicio o al final es un error que se le muestra a la persona
 */
export function nombrePersona(mensajeVacio = 'Escribe el nombre') {
  return z
    .string()
    .refine((valor) => valor.trim() !== '', mensajeVacio)
    .refine((valor) => valor.trim() === '' || PATRON_NOMBRE.test(valor), MENSAJE_NOMBRE)
}

/** Deja solo los dígitos de un texto, hasta `maximo` (lo usan los campos de teléfono y documento) */
export function soloDigitos(valor: string, maximo: number): string {
  return valor.replace(/\D/g, '').slice(0, maximo)
}

/** Teléfono obligatorio: 10 dígitos, solo números. Sin trim: un espacio también es un símbolo */
export function telefono(mensajeVacio = 'Escribe el teléfono') {
  return z.string().min(1, mensajeVacio).regex(PATRON_TELEFONO, MENSAJE_TELEFONO)
}

/** Número de documento obligatorio: de 6 a 10 dígitos, solo números */
export function documento(mensajeVacio = 'Escribe el documento') {
  return z.string().min(1, mensajeVacio).regex(PATRON_DOCUMENTO, MENSAJE_DOCUMENTO)
}

/** Número de documento opcional (ocupantes): vacío, o de 6 a 10 dígitos */
export function documentoOpcional() {
  return z.string().refine((valor) => valor === '' || PATRON_DOCUMENTO.test(valor), MENSAJE_DOCUMENTO)
}

/** Contraseña nueva: 8 a 72 caracteres, con una mayúscula y un número */
export function contrasenaNueva(mensajeVacio = 'Escribe la contraseña') {
  return z
    .string()
    .min(1, mensajeVacio)
    .min(8, 'La contraseña debe tener entre 8 y 72 caracteres')
    .max(72, 'La contraseña debe tener entre 8 y 72 caracteres')
    .regex(PATRON_CONTRASENA, 'La contraseña debe tener al menos una mayúscula y un número')
}

/** La URL de una imagen es válida: https:// y sin espacios */
export const esUrlImagen = (valor: string) => PATRON_URL.test(valor)

/** URL de imagen opcional (foto del perfil): vacía, o https:// sin espacios */
export function urlOpcional(maximo = 500) {
  return z
    .string()
    .trim()
    .max(maximo, `La URL admite máximo ${maximo} caracteres`)
    .refine((valor) => valor === '' || PATRON_URL.test(valor), MENSAJE_URL_IMAGEN)
}

/** URL de imagen obligatoria (imágenes del apartamento): https:// sin espacios */
export function urlObligatoria(mensaje = 'Escribe la URL', maximo = 500) {
  return z
    .string()
    .trim()
    .min(1, mensaje)
    .max(maximo, `La URL admite máximo ${maximo} caracteres`)
    .regex(PATRON_URL, MENSAJE_URL_IMAGEN)
}

// ---------- Fechas ----------

/** Fecha "yyyy-MM-dd" obligatoria */
export function fecha(mensaje: string) {
  return z.string().min(1, mensaje).refine(esFechaISO, 'Escribe una fecha válida')
}

/** Fecha más antigua que se admite como nacimiento ("yyyy-MM-dd"): hoy hace 110 años */
export function fechaMinimaNacimiento(): string {
  return format(subYears(new Date(), ANIOS_MAXIMOS_NACIMIENTO), 'yyyy-MM-dd')
}

/** Fecha de nacimiento: obligatoria, pasada y de hace 110 años o menos */
export function fechaPasada(mensaje: string) {
  return fecha(mensaje)
    .refine((valor) => valor < hoy(), 'La fecha de nacimiento debe ser pasada')
    .refine((valor) => valor >= fechaMinimaNacimiento(), MENSAJE_NACIMIENTO_ANTIGUO)
}

/** Fecha de nacimiento de un ocupante: obligatoria, no futura y de hace 110 años o menos */
export function fechaNoFutura(mensaje: string) {
  return fecha(mensaje)
    .refine((valor) => valor <= hoy(), 'La fecha de nacimiento no puede ser futura')
    .refine((valor) => valor >= fechaMinimaNacimiento(), MENSAJE_NACIMIENTO_ANTIGUO)
}

/**
 * La fecha final de un objeto va después de la inicial (o igual, con `permiteIgual`). Solo compara cuando las dos
 * son fechas válidas; los vacíos los marca el esquema de cada campo.
 */
export function finDespuesDeInicio<T extends Record<string, unknown>>(
  inicio: keyof T & string,
  fin: keyof T & string,
  mensaje: string,
  permiteIgual = false,
) {
  return (datos: T, contexto: z.RefinementCtx) => {
    const a = datos[inicio]
    const b = datos[fin]
    if (typeof a !== 'string' || typeof b !== 'string' || !esFechaISO(a) || !esFechaISO(b)) return
    if (permiteIgual ? b < a : b <= a) contexto.addIssue({ code: 'custom', path: [fin], message: mensaje })
  }
}

/** Fecha opcional: vacía o "yyyy-MM-dd" válida */
export function fechaOpcional() {
  return z
    .string()
    .optional()
    .refine((valor) => !valor || esFechaISO(valor), 'Escribe una fecha válida')
}

/**
 * Rango de un filtro de consulta (desde y hasta, ambas incluidas). Cada fecha es opcional salvo que se pida; si
 * están las dos, hasta no va antes de desde.
 */
export function rangoFiltro({ obligatorio = false }: { obligatorio?: boolean } = {}) {
  const campo = (mensaje: string) => (obligatorio ? fecha(mensaje) : fechaOpcional())
  return z
    .object({ desde: campo('Elige la fecha desde'), hasta: campo('Elige la fecha hasta') })
    .superRefine(finDespuesDeInicio('desde', 'hasta', 'Debe ser igual o posterior a la fecha desde', true))
}

// ---------- Números escritos en un campo de texto ----------

/** Entero escrito (sin decimales) dentro de un rango */
export function entero(mensaje: string, { minimo = 0, maximo }: { minimo?: number; maximo?: number } = {}) {
  return z
    .string()
    .trim()
    .min(1, mensaje)
    .refine(
      (valor) => /^-?\d+$/.test(valor) && Number(valor) >= minimo && (maximo === undefined || Number(valor) <= maximo),
      mensaje,
    )
}

/** Valor en pesos mayor que cero y sin decimales */
export function pesos(mensaje = 'Escribe un valor mayor que cero, sin decimales') {
  return z
    .string()
    .trim()
    .min(1, mensaje)
    .refine((valor) => {
      const numero = aPesos(valor)
      return Number.isSafeInteger(numero) && numero > 0
    }, mensaje)
}

/** Valor en pesos opcional: vacío o mayor que cero sin decimales */
export function pesosOpcional(mensaje = 'Escribe un valor mayor que cero, sin decimales') {
  return z
    .string()
    .trim()
    .refine((valor) => {
      if (valor === '') return true
      const numero = aPesos(valor)
      return Number.isSafeInteger(numero) && numero > 0
    }, mensaje)
}

/** Número decimal escrito dentro de un rango */
export function decimal(mensaje: string, minimo: number, maximo: number) {
  return z
    .string()
    .trim()
    .min(1, mensaje)
    .refine((valor) => {
      const numero = Number(valor.replace(',', '.'))
      return /^-?\d+([.,]\d+)?$/.test(valor) && numero >= minimo && numero <= maximo
    }, mensaje)
}

// ---------- Validar y mostrar ----------

/** Ruta de zod ("ocupantes", 0, "nombre") → clave del formulario ("ocupantes.0.nombre") */
const claveDe = (ruta: PropertyKey[]) => ruta.map(String).join('.')

/** Valida con el esquema. Devuelve los datos limpios o, si algo falla, el primer error de cada campo. */
export function validar<T>(
  esquema: z.ZodType<T>,
  datos: unknown,
): { exito: true; datos: T; errores: Errores } | { exito: false; datos?: undefined; errores: Errores } {
  const resultado = esquema.safeParse(datos)
  if (resultado.success) return { exito: true, datos: resultado.data, errores: {} }
  const errores: Errores = {}
  for (const problema of resultado.error.issues) {
    const clave = claveDe(problema.path)
    if (errores[clave] === undefined) errores[clave] = problema.message
  }
  return { exito: false, errores }
}

/**
 * Validación de un formulario con estado propio. Los errores aparecen desde el primer intento de envío y desde ahí
 * se recalculan con cada cambio. `comprobar` marca los errores y devuelve los datos solo si no hay ninguno, así
 * el formulario no envía nada mientras haya errores.
 */
export function useValidacion<T>(esquema: z.ZodType<T>, datos: unknown) {
  const [intentado, setIntentado] = useState(false)
  const resultado = validar(esquema, datos)
  return {
    errores: intentado ? resultado.errores : ({} as Errores),
    hayErrores: intentado && !resultado.exito,
    comprobar: (): T | null => {
      setIntentado(true)
      return resultado.exito ? resultado.datos : null
    },
    reiniciar: () => setIntentado(false),
  }
}

/**
 * Errores por campo de un 400 del backend, con los nombres del formulario. Las rutas anidadas del backend
 * ("ocupantes[1].nombre") se escriben como las de zod ("ocupantes.1.nombre"); `renombrar` traduce las que tengan
 * otro nombre en el formulario.
 */
export function erroresDelServidor(error: unknown, renombrar: Record<string, string> = {}): Errores {
  if (!esErrorApi(error) || error.status !== 400) return {}
  const errores: Errores = {}
  Object.entries(error.errores).forEach(([campo, mensaje]) => {
    const clave = campo.replace(/\[(\d+)\]/g, '.$1')
    errores[renombrar[clave] ?? renombrar[campo] ?? clave] = mensaje
  })
  return errores
}

/** El error es un 400 que trae errores por campo: se muestran en los campos y no en un aviso general */
export function esErrorDeCampos(error: unknown): boolean {
  return esErrorApi(error) && error.status === 400 && Object.keys(error.errores).length > 0
}

/** Combina los errores locales con los del servidor; manda el local */
export function unirErrores(...fuentes: Errores[]): Errores {
  const unidos: Errores = {}
  for (const fuente of fuentes) {
    Object.entries(fuente).forEach(([clave, mensaje]) => {
      if (mensaje && unidos[clave] === undefined) unidos[clave] = mensaje
    })
  }
  return unidos
}

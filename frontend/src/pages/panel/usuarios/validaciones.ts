import { z } from 'zod'
import type { Rol } from '@/api/tipos'
import { cumpleEdadMinima, MENSAJE_EDAD_MINIMA } from '@/lib/edadMinima'
import { contrasenaNueva, correo, documento, fechaPasada, nombrePersona, telefono } from '@/lib/validacion'
import { ROLES } from './listaRoles'

/** Usuario nuevo del panel: las reglas de CrearUsuarioRequest */
export const esquemaCrearUsuario = z.object({
  nombre: nombrePersona(),
  correo: correo(),
  contrasena: contrasenaNueva(),
  telefono: telefono(),
  documento: documento(),
  fechaNacimiento: fechaPasada('Escribe la fecha de nacimiento').refine(cumpleEdadMinima, MENSAJE_EDAD_MINIMA),
  rol: z.enum(ROLES as [Rol, ...Rol[]], 'Elige el rol'),
})

/** Cambio de rol: uno de los roles y distinto del actual */
export function esquemaCambioRol(actual: Rol) {
  return z.object({
    rol: z
      .enum(ROLES as [Rol, ...Rol[]], 'Elige el rol')
      .refine((rol) => rol !== actual, 'Elige un rol distinto al actual'),
  })
}

import type { Rol } from '@/api/tipos'
import type { EstadoDesde } from './AuthContext'
import { ROLES_ADMIN, ROLES_HUESPED, ROLES_PANEL, ROLES_SERVICIO, rutaInicioPorRol, tieneAlgunRol } from './roles'

const RUTAS_DE_ACCESO = ['/login', '/registro', '/recuperar', '/recuperar/codigo', '/sin-permiso']

/** Páginas del panel que solo ve la administración (las mismas que protege el router con ROLES_ADMIN) */
const SOLO_ADMINISTRACION = [
  /^\/panel\/apartamentos\/nuevo\/?$/,
  /^\/panel\/apartamentos\/[^/]+\/(editar|novedades)\/?$/,
  /^\/panel\/(temporadas|politica|alojamiento|usuarios|canales|reportes)(\/|$)/,
]

/** Páginas exclusivas del huésped */
const SOLO_HUESPED = /^\/(reservar|mis-reservas|perfil)(\/|$)/

/**
 * Roles que pueden abrir una ruta, con las mismas reglas del router. null: es pública (la ve cualquiera, con o sin
 * sesión).
 */
export function rolesDeRuta(pathname: string): Rol[] | null {
  if (pathname.startsWith('/panel')) {
    return SOLO_ADMINISTRACION.some((patron) => patron.test(pathname)) ? ROLES_ADMIN : ROLES_PANEL
  }
  if (pathname.startsWith('/servicio')) return ROLES_SERVICIO
  if (SOLO_HUESPED.test(pathname)) return ROLES_HUESPED
  return null
}

/**
 * Destino después de iniciar sesión. Vuelve a la ruta de la que venía solo si el rol que entró puede abrirla; si no,
 * va al inicio de su rol. Así una ruta guardada por otra persona en la misma pestaña (por ejemplo /servicio antes
 * de que entre la administración) no lleva a una pantalla ajena. Las páginas públicas solo se retoman para el
 * huésped: el equipo empieza siempre en su propio inicio.
 */
export function rutaDespuesDeLogin(roles: Rol[], estado: unknown): string {
  const desde = (estado as EstadoDesde | null)?.desde
  if (desde?.pathname && !RUTAS_DE_ACCESO.includes(desde.pathname)) {
    const requeridos = rolesDeRuta(desde.pathname)
    const puede = requeridos === null ? tieneAlgunRol(roles, ROLES_HUESPED) : tieneAlgunRol(roles, requeridos)
    if (puede) return desde.pathname + (desde.search ?? '')
  }
  return rutaInicioPorRol(roles)
}

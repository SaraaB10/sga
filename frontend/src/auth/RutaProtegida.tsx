import type { ReactNode } from 'react'
import { Navigate, Outlet, useLocation } from 'react-router-dom'
import type { Rol } from '@/api/tipos'
import { CargandoPagina } from '@/components/coral/CargandoPagina'
import { tieneAlgunRol } from './roles'
import { useAuth, type EstadoDesde } from './AuthContext'

interface Props {
  /** Roles que pueden entrar; si no se indica, basta con haber iniciado sesión */
  roles?: Rol[]
  children?: ReactNode
}

/**
 * Sin sesión → /login (y vuelve aquí al iniciar sesión, si el rol que entra puede).
 * Con sesión, espera a que cargue el usuario antes de decidir: así no aparece "No tienes permiso" un instante
 * mientras cambia la sesión. Con el usuario cargado y sin el rol → /sin-permiso.
 */
export function RutaProtegida({ roles, children }: Props) {
  const { sesion, vencida, saliendo, cargandoUsuario } = useAuth()
  const location = useLocation()

  // Quien cierra sesión va al inicio, no al login: no se guarda su ruta para la próxima persona
  if (!sesion && saliendo) return <CargandoPagina />

  if (!sesion) {
    const estado: EstadoDesde = { desde: { pathname: location.pathname, search: location.search } }
    return <Navigate to={vencida ? '/login?expirado=1' : '/login'} replace state={estado} />
  }

  if (cargandoUsuario) return <CargandoPagina />

  if (roles && !tieneAlgunRol(sesion.roles, roles)) {
    return <Navigate to="/sin-permiso" replace />
  }

  return children ? <>{children}</> : <Outlet />
}

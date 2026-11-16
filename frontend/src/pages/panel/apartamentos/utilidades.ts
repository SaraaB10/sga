import { useAuth } from '@/auth/AuthContext'

/** Las acciones de cambio sobre apartamentos son solo del administrador */
export function useEsAdministrador(): boolean {
  return useAuth().roles.includes('ADMINISTRADOR')
}

export { aPesos } from '@/lib/validacion'

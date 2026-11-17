import { useState } from 'react'
import { toast } from 'sonner'
import { useCambiarRolUsuario, useCrearUsuario, type CrearUsuarioRequest } from '@/api/usuarios'
import type { Rol, UsuarioResponse } from '@/api/tipos'
import { NOMBRE_ROL } from '@/auth/roles'
import { Campo, InputContrasena, InputDigitos } from '@/components/coral'
import { Input } from '@/components/ui/input'
import { cumpleEdadMinima, esFechaCompleta, fechaMaximaNacimientoCuenta, mensajeEdadMinima } from '@/lib/edadMinima'
import { useReglasAlojamiento } from '@/pages/publico/comun/consultas'
import {
  AYUDA_CONTRASENA,
  DIGITOS_TELEFONO,
  erroresDelServidor,
  fechaMinimaNacimiento,
  MAXIMO_DIGITOS_DOCUMENTO,
  MAXIMO_NOMBRE_PERSONA,
  unirErrores,
  useValidacion,
} from '@/lib/validacion'
import { DialogoFormulario } from '@/pages/panel/folio/DialogoFormulario'
import { useAlAbrir } from '@/pages/panel/folio/useAlAbrir'
import { claseSelect } from '@/pages/publico/comun/estilos'
import { ROLES } from './listaRoles'
import { esquemaCambioRol, esquemaCrearUsuario } from './validaciones'

interface Base {
  abierto: boolean
  alCambiar: (abierto: boolean) => void
}

// ---------- Crear usuario ----------

type Datos = Omit<CrearUsuarioRequest, 'rol'> & { rol: Rol }
const VACIO: Datos = {
  nombre: '',
  correo: '',
  contrasena: '',
  telefono: '',
  documento: '',
  fechaNacimiento: '',
  rol: 'RECEPCIONISTA',
}

export function DialogoCrearUsuario({ abierto, alCambiar }: Base) {
  const crear = useCrearUsuario()
  const [datos, setDatos] = useState<Datos>(VACIO)
  // La edad mínima se avisa en cuanto la fecha está completa, sin esperar al envío
  const [avisoEdad, setAvisoEdad] = useState<string>()
  const edadMinima = useReglasAlojamiento().reglas.edadMinimaCuenta
  const validacion = useValidacion(esquemaCrearUsuario(edadMinima), datos)

  useAlAbrir(abierto, () => {
    setDatos(VACIO)
    setAvisoEdad(undefined)
    validacion.reiniciar()
    crear.reset()
  })

  const campo = (clave: keyof Datos) => ({
    value: datos[clave],
    onChange: (e: { target: { value: string } }) => setDatos((d) => ({ ...d, [clave]: e.target.value })),
  })
  const errores = unirErrores(validacion.errores, { fechaNacimiento: avisoEdad }, erroresDelServidor(crear.error))
  const error = (clave: keyof Datos) => errores[clave]

  const enviar = () => {
    const limpios = validacion.comprobar()
    if (!limpios) return

    crear.mutate(
      limpios,
      {
        onSuccess: (u) => {
          toast.success('Usuario creado', { description: `${u.nombre} · ${NOMBRE_ROL[u.rol]}` })
          alCambiar(false)
        },
      },
    )
  }

  return (
    <DialogoFormulario
      id="form-usuario"
      abierto={abierto}
      alCambiar={alCambiar}
      titulo="Nuevo usuario"
      descripcion="Crea una cuenta de cualquier rol. La persona inicia sesión con este correo y esta contraseña."
      textoEnviar="Crear usuario"
      cargando={crear.isPending}
      error={crear.error}
      tituloError="No se pudo crear el usuario"
      className="sm:max-w-lg"
      alEnviar={enviar}
    >
      <div className="grid gap-4 sm:grid-cols-2">
        <Campo id="usuario-nombre" etiqueta="Nombre" error={error('nombre')} className="sm:col-span-2">
          <Input autoComplete="off" maxLength={MAXIMO_NOMBRE_PERSONA} {...campo('nombre')} />
        </Campo>
        <Campo id="usuario-correo" etiqueta="Correo" error={error('correo')} className="sm:col-span-2">
          <Input type="email" autoComplete="off" maxLength={120} {...campo('correo')} />
        </Campo>
        <Campo id="usuario-contrasena" etiqueta="Contraseña" error={error('contrasena')} ayuda={AYUDA_CONTRASENA} className="sm:col-span-2">
          <InputContrasena autoComplete="new-password" maxLength={72} {...campo('contrasena')} />
        </Campo>
        <Campo id="usuario-telefono" etiqueta="Teléfono" error={error('telefono')}>
          <InputDigitos maximo={DIGITOS_TELEFONO} {...campo('telefono')} />
        </Campo>
        <Campo id="usuario-documento" etiqueta="Documento" error={error('documento')}>
          <InputDigitos maximo={MAXIMO_DIGITOS_DOCUMENTO} {...campo('documento')} />
        </Campo>
        <Campo id="usuario-nacimiento" etiqueta="Fecha de nacimiento" error={error('fechaNacimiento')}>
          <Input
            type="date"
            min={fechaMinimaNacimiento()}
            max={fechaMaximaNacimientoCuenta(edadMinima)}
            value={datos.fechaNacimiento}
            onChange={(e) => {
              const valor = e.target.value
              setDatos((d) => ({ ...d, fechaNacimiento: valor }))
              setAvisoEdad(
                esFechaCompleta(valor) && !cumpleEdadMinima(valor, edadMinima) ? mensajeEdadMinima(edadMinima) : undefined,
              )
            }}
          />
        </Campo>
        <Campo id="usuario-rol" etiqueta="Rol" error={error('rol')}>
          <select className={claseSelect} {...campo('rol')}>
            {ROLES.map((r) => (
              <option key={r} value={r}>
                {NOMBRE_ROL[r]}
              </option>
            ))}
          </select>
        </Campo>
      </div>
    </DialogoFormulario>
  )
}

// ---------- Cambiar rol ----------

export function DialogoCambiarRol({ usuario, abierto, alCambiar }: Base & { usuario: UsuarioResponse | null }) {
  const cambiar = useCambiarRolUsuario()
  const [rol, setRol] = useState<Rol>('HUESPED')
  const validacion = useValidacion(esquemaCambioRol(usuario?.rol ?? 'HUESPED'), { rol })

  useAlAbrir(abierto, () => {
    setRol(usuario?.rol ?? 'HUESPED')
    validacion.reiniciar()
    cambiar.reset()
  })

  if (!usuario) return null
  const aviso = validacion.errores.rol ?? erroresDelServidor(cambiar.error).rol

  const enviar = () => {
    if (!validacion.comprobar()) return
    cambiar.mutate(
      { id: usuario.id, rol },
      {
        onSuccess: (u) => {
          toast.success('Rol cambiado', { description: `${u.nombre} ahora es ${NOMBRE_ROL[u.rol].toLowerCase()}` })
          alCambiar(false)
        },
      },
    )
  }

  return (
    <DialogoFormulario
      id="form-rol"
      abierto={abierto}
      alCambiar={alCambiar}
      titulo={`Cambiar el rol de ${usuario.nombre}`}
      descripcion={`Rol actual: ${NOMBRE_ROL[usuario.rol]}.`}
      textoEnviar="Cambiar rol"
      cargando={cambiar.isPending}
      error={cambiar.error}
      tituloError="No se pudo cambiar el rol"
      alEnviar={enviar}
    >
      <Campo id="rol-nuevo" etiqueta="Nuevo rol" error={aviso}>
        <select className={claseSelect} value={rol} onChange={(e) => setRol(e.target.value as Rol)}>
          {ROLES.map((r) => (
            <option key={r} value={r}>
              {NOMBRE_ROL[r]}
              {r === usuario.rol ? ' (actual)' : ''}
            </option>
          ))}
        </select>
      </Campo>
    </DialogoFormulario>
  )
}

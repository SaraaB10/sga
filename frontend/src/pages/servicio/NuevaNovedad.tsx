import { useNavigate, useSearchParams } from 'react-router-dom'
import { toast } from 'sonner'
import { FormularioNovedad } from './FormularioNovedad'

/** Pantalla del personal de servicio para reportar una novedad desde el celular */
export default function NuevaNovedad() {
  const [parametros] = useSearchParams()
  const navigate = useNavigate()

  return (
    <>
      <h1 className="sr-only">Reportar una novedad</h1>
      <FormularioNovedad
        grande
        apartamentoInicial={parametros.get('apartamento') ?? ''}
        alRegistrar={(novedad) => {
          toast.success('Novedad registrada', { description: novedad.identificacionApartamento })
          navigate(`/servicio/apartamentos/${encodeURIComponent(novedad.identificacionApartamento)}`)
        }}
      />
    </>
  )
}

import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { ArrowLeft, FilePlus2 } from 'lucide-react'
import { toast } from 'sonner'
import { useApartamento } from '@/api/publico'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { FormularioNovedad } from '@/pages/servicio/FormularioNovedad'
import { ListaNovedades } from '@/pages/servicio/ListaNovedades'

/** Historial de novedades de un apartamento en el panel del administrador, que también puede registrar una (CU-17) */
export default function NovedadesApartamento() {
  const { id = '' } = useParams()
  const apartamento = useApartamento(id)
  const [registrando, setRegistrando] = useState(false)
  // Cada apertura monta un formulario limpio
  const [formulario, setFormulario] = useState(0)

  const abrir = () => {
    setFormulario((n) => n + 1)
    setRegistrando(true)
  }

  return (
    <section className="mx-auto w-full max-w-3xl">
      <Link
        to={`/panel/apartamentos/${encodeURIComponent(id)}`}
        className="inline-flex items-center gap-1.5 text-sm font-medium text-muted-foreground hover:text-foreground"
      >
        <ArrowLeft aria-hidden="true" className="size-4" /> {id}
      </Link>
      <header className="mt-2 flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="font-titulo text-3xl font-semibold">Novedades</h1>
          <p className="text-sm text-muted-foreground">
            {apartamento.data ? `${apartamento.data.identificacion} · ${apartamento.data.nombre}` : id}
          </p>
        </div>
        <Button onClick={abrir}>
          <FilePlus2 aria-hidden="true" /> Registrar novedad
        </Button>
      </header>
      <ListaNovedades identificacion={id} className="mt-6" />

      <Dialog open={registrando} onOpenChange={setRegistrando}>
        <DialogContent className="sm:max-w-lg">
          <DialogHeader>
            <DialogTitle>Registrar novedad</DialogTitle>
            <DialogDescription>
              Daño, faltante o situación en {apartamento.data ? `${id} · ${apartamento.data.nombre}` : id}. La IA
              sugiere la gravedad, pero la eliges tú.
            </DialogDescription>
          </DialogHeader>
          <FormularioNovedad
            key={formulario}
            apartamentoFijo={id}
            alRegistrar={(novedad) => {
              toast.success('Novedad registrada', { description: novedad.identificacionApartamento })
              setRegistrando(false)
            }}
          />
        </DialogContent>
      </Dialog>
    </section>
  )
}

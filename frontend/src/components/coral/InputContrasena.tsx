import { useState, type ComponentProps } from 'react'
import { Eye, EyeOff } from 'lucide-react'
import { Input } from '@/components/ui/input'
import { cn } from '@/lib/utils'

type Props = Omit<ComponentProps<'input'>, 'type'>

/**
 * Campo de contraseña con un botón de ojo que muestra u oculta lo escrito. Recibe las mismas props que Input
 * (también el ref de react-hook-form y el id, aria-invalid y aria-describedby que le pasa Campo).
 */
export function InputContrasena({ className, disabled, ...props }: Props) {
  const [visible, setVisible] = useState(false)
  const etiqueta = visible ? 'Ocultar contraseña' : 'Mostrar contraseña'
  const Icono = visible ? EyeOff : Eye

  return (
    <div className="relative">
      <Input
        {...props}
        type={visible ? 'text' : 'password'}
        disabled={disabled}
        // Sin autocorrección ni mayúscula inicial cuando el texto queda a la vista
        autoCapitalize="none"
        autoCorrect="off"
        spellCheck={false}
        className={cn('pr-12', className)}
      />
      <button
        type="button"
        aria-label={etiqueta}
        aria-pressed={visible}
        title={etiqueta}
        disabled={disabled}
        onClick={() => setVisible((v) => !v)}
        className="absolute top-1/2 right-1 inline-flex size-9 -translate-y-1/2 items-center justify-center rounded-campo text-muted-foreground transition-colors outline-none hover:bg-muted hover:text-foreground focus-visible:ring-3 focus-visible:ring-ring/50 disabled:pointer-events-none disabled:opacity-50"
      >
        <Icono aria-hidden="true" className="size-4" />
      </button>
    </div>
  )
}

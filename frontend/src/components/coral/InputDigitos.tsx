import type { ChangeEvent, ComponentProps } from 'react'
import { Input } from '@/components/ui/input'
import { soloDigitos } from '@/lib/validacion'

type Props = Omit<ComponentProps<'input'>, 'type' | 'inputMode' | 'maxLength'> & {
  /** Cantidad máxima de dígitos que deja escribir */
  maximo: number
}

/**
 * Campo que solo admite dígitos (teléfono y documento): descarta letras, espacios y símbolos al escribir o pegar y
 * no deja pasar de `maximo`. Recibe las mismas props que Input, también el ref de react-hook-form y lo que le pasa
 * Campo; el onChange recibe el valor ya limpio.
 */
export function InputDigitos({ maximo, onChange, ...props }: Props) {
  const alCambiar = (e: ChangeEvent<HTMLInputElement>) => {
    const limpio = soloDigitos(e.target.value, maximo)
    if (limpio !== e.target.value) e.target.value = limpio
    onChange?.(e)
  }

  return (
    <Input
      autoComplete="off"
      {...props}
      type="text"
      inputMode="numeric"
      pattern="[0-9]*"
      maxLength={maximo}
      onChange={alCambiar}
    />
  )
}

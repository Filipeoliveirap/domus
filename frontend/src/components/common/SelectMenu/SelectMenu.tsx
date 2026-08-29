'use client'

import { useEffect, useRef, useState } from 'react'
import { ChevronDown, Check } from 'lucide-react'
import { clsx } from 'clsx'
import { useClickFora } from '@/hooks/useClickFora'
import styles from './SelectMenu.module.css'

export interface OpcaoSelectMenu {
  value: string
  label: string
}

interface SelectMenuProps {
  value: string
  onChange: (valor: string) => void
  options: OpcaoSelectMenu[]
  /** Rótulo mostrado quando `value` é vazio (também vira a 1ª opção, valor ''). */
  placeholder?: string
  className?: string
  ariaLabel?: string
  disabled?: boolean
}

/**
 * Dropdown single-select com o visual do site (não o `<select>` nativo "quadradão").
 * Popup animado, `useClickFora` + Escape, opção marcada. Fecha ao escolher.
 */
export function SelectMenu({
  value,
  onChange,
  options,
  placeholder = 'Selecionar',
  className,
  ariaLabel,
  disabled = false,
}: SelectMenuProps) {
  const [aberto, setAberto] = useState(false)
  const ref = useRef<HTMLDivElement>(null)
  useClickFora(ref, () => setAberto(false))

  useEffect(() => {
    if (!aberto) return
    const aoTeclar = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setAberto(false)
    }
    document.addEventListener('keydown', aoTeclar)
    return () => document.removeEventListener('keydown', aoTeclar)
  }, [aberto])

  const todas: OpcaoSelectMenu[] = [{ value: '', label: placeholder }, ...options]
  const selecionada = todas.find((o) => o.value === value)

  function escolher(valor: string) {
    onChange(valor)
    setAberto(false)
  }

  return (
    <div className={clsx(styles.container, className)} ref={ref}>
      <button
        type="button"
        className={styles.gatilho}
        onClick={() => !disabled && setAberto((a) => !a)}
        disabled={disabled}
        aria-haspopup="listbox"
        aria-expanded={aberto}
        aria-label={ariaLabel}
      >
        <span className={clsx(styles.valor, !value && styles.placeholder)}>
          {selecionada?.label ?? placeholder}
        </span>
        <ChevronDown size={16} className={clsx(styles.chevron, aberto && styles.chevronAberto)} aria-hidden="true" />
      </button>

      {aberto && (
        <div className={styles.painel} role="listbox">
          {todas.map((o) => (
            <button
              key={o.value || '__vazio__'}
              type="button"
              role="option"
              aria-selected={o.value === value}
              className={clsx(styles.opcao, o.value === value && styles.opcaoAtiva)}
              onClick={() => escolher(o.value)}
            >
              <span>{o.label}</span>
              {o.value === value && <Check size={14} aria-hidden="true" />}
            </button>
          ))}
        </div>
      )}
    </div>
  )
}

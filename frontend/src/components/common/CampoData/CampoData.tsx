'use client'

import { useRef, useState } from 'react'
import { CalendarDays } from 'lucide-react'
import { formatarDataDigitada } from '@/lib/masks'
import styles from './CampoData.module.css'

interface Props {
  value: string
  onChange: (iso: string) => void
  label?: string
  id?: string
  erro?: string
  min?: string
  max?: string
  disabled?: boolean
  semLabel?: boolean
}

function isoParaBR(iso: string): string {
  const [a, m, d] = (iso || '').split('-')
  return a && m && d ? `${d}/${m}/${a}` : ''
}

function brParaISO(br: string): string {
  const [d, m, a] = (br || '').split('/')
  if (!d || !m || a?.length !== 4) return ''
  return `${a}-${m}-${d}`
}

export function CampoData({
  value, onChange, label, id, erro, min, max, disabled, semLabel,
}: Props) {
  const seletorRef = useRef<HTMLInputElement>(null)
  const [textoLocal, setTextoLocal] = useState<string | null>(null)
  const exibido = textoLocal ?? isoParaBR(value)

  function aoDigitar(bruto: string) {
    const formatado = formatarDataDigitada(bruto)
    setTextoLocal(formatado)
    onChange(brParaISO(formatado))
  }

  function abrirCalendario() {
    const el = seletorRef.current
    if (!el || disabled) return
    // showPicker() não existe em navegadores antigos; focar é o fallback natural.
    if (typeof el.showPicker === 'function') el.showPicker()
    else el.focus()
  }

  return (
    <div className={styles.campo}>
      {!semLabel && label && (
        <label className={styles.label} htmlFor={id}>
          {label}
        </label>
      )}

      <div className={styles.caixa}>
        <input
          id={id}
          type="text"
          inputMode="numeric"
          placeholder="dd/mm/aaaa"
          maxLength={10}
          className={styles.entrada}
          value={exibido}
          disabled={disabled}
          aria-label={semLabel ? label : undefined}
          onChange={(e) => aoDigitar(e.target.value)}
          onBlur={() => setTextoLocal(null)}
        />

        <button
          type="button"
          className={styles.botaoCalendario}
          onClick={abrirCalendario}
          aria-label={label ? `Abrir calendário — ${label}` : 'Abrir calendário'}
          tabIndex={-1}
          disabled={disabled}
        >
          <CalendarDays size={16} aria-hidden="true" />
        </button>

        <input
          ref={seletorRef}
          type="date"
          className={styles.seletorOculto}
          tabIndex={-1}
          aria-hidden="true"
          value={value || ''}
          min={min}
          max={max}
          onChange={(e) => {
            setTextoLocal(isoParaBR(e.target.value))
            onChange(e.target.value)
          }}
        />
      </div>

      {erro && <span className={styles.erro}>{erro}</span>}
    </div>
  )
}

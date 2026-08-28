'use client'

import { useState } from 'react'
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
  const [textoLocal, setTextoLocal] = useState<string | null>(null)
  const exibido = textoLocal ?? isoParaBR(value)

  function aoDigitar(bruto: string) {
    const formatado = formatarDataDigitada(bruto)
    setTextoLocal(formatado)
    onChange(brParaISO(formatado))
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

        <span className={styles.botaoCalendario} aria-hidden="true">
          <CalendarDays size={16} />
        </span>

        {/*
          O seletor nativo de data é um overlay TRANSPARENTE de tamanho real por cima do
          ícone — não um input de 1px escondido. iOS/Safari só abre o calendário nativo
          num toque real do usuário sobre um input visível/interativo; `showPicker()` (a
          abordagem anterior) não existe no Safari e `.focus()` programático não abre nada
          no iPhone. Assim o toque no ícone é, de fato, um toque no `type="date"`, e os
          três (iOS, Android, desktop) abrem o picker nativo sozinhos.
        */}
        <input
          type="date"
          className={styles.seletorData}
          tabIndex={-1}
          aria-label={label ? `Escolher data no calendário — ${label}` : 'Escolher data no calendário'}
          value={value || ''}
          min={min}
          max={max}
          disabled={disabled}
          onChange={(e) => {
            setTextoLocal(isoParaBR(e.target.value))
            onChange(e.target.value)
          }}
          onClick={(e) => {
            // Progressive enhancement pra Firefox/desktop, onde clicar na área não abre
            // sozinho. iOS ignora (não implementa) e abre pelo próprio toque.
            const el = e.currentTarget
            if (typeof el.showPicker === 'function') {
              try { el.showPicker() } catch { /* gesto inválido / não suportado */ }
            }
          }}
        />
      </div>

      {erro && <span className={styles.erro}>{erro}</span>}
    </div>
  )
}

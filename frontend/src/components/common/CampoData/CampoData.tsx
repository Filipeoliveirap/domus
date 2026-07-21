'use client'

import { useRef } from 'react'
import { CalendarDays } from 'lucide-react'
import { formatarDataDigitada } from '@/lib/masks'
import styles from './CampoData.module.css'

interface Props {
  value: string
  onChange: (valor: string) => void
  label: string
  id?: string
  erro?: string
}

/** `dd/mm/aaaa` (o que a pessoa vê) → `aaaa-mm-dd` (o que o seletor nativo entende). */
function paraISO(br: string): string {
  const [d, m, a] = br.split('/')
  return d && m && a?.length === 4 ? `${a}-${m}-${d}` : ''
}

function paraBR(iso: string): string {
  const [a, m, d] = iso.split('-')
  return a && m && d ? `${d}/${m}/${a}` : ''
}

/**
 * Campo de data com as duas coisas que o formulário precisava e não tinha juntas.
 *
 * <p><b>O problema:</b> `<input type="date">` sozinho dá o calendário, mas exibe no idioma do
 * NAVEGADOR — quem tem o sistema em inglês vê `mm/dd/aaaa` e digita a data trocada. Texto
 * mascarado sozinho garante o `dd/mm/aaaa`, mas perde o calendário.
 *
 * <p><b>A solução:</b> o texto mascarado é o campo de verdade (sempre pt-BR, sempre digitável);
 * o `input[type=date]` fica escondido atrás do ícone e só serve para abrir o seletor nativo via
 * `showPicker()`. Quem prefere digitar, digita; quem prefere clicar, clica.
 */
export function CampoData({ value, onChange, label, id, erro }: Props) {
  const seletorRef = useRef<HTMLInputElement>(null)

  function abrirCalendario() {
    const el = seletorRef.current
    if (!el) return
    // showPicker() não existe em navegadores antigos; o clique é o fallback natural.
    if (typeof el.showPicker === 'function') el.showPicker()
    else el.focus()
  }

  return (
    <div className={styles.campo}>
      <label className={styles.label} htmlFor={id}>
        {label}
      </label>

      <div className={styles.caixa}>
        <input
          id={id}
          type="text"
          inputMode="numeric"
          placeholder="dd/mm/aaaa"
          maxLength={10}
          className={styles.entrada}
          value={value}
          onChange={(e) => onChange(formatarDataDigitada(e.target.value))}
        />

        <button
          type="button"
          className={styles.botaoCalendario}
          onClick={abrirCalendario}
          aria-label={`Abrir calendário — ${label}`}
          tabIndex={-1}
        >
          <CalendarDays size={16} aria-hidden="true" />
        </button>

        {/*
          Fora do fluxo de foco (tabIndex -1, aria-hidden): quem navega por teclado usa o
          campo de texto, que já aceita a data digitada. Este existe só pelo calendário.
        */}
        <input
          ref={seletorRef}
          type="date"
          className={styles.seletorOculto}
          tabIndex={-1}
          aria-hidden="true"
          value={paraISO(value)}
          onChange={(e) => onChange(paraBR(e.target.value))}
        />
      </div>

      {erro && <span className={styles.erro}>{erro}</span>}
    </div>
  )
}

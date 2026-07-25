'use client'

import { useRef, useState } from 'react'
import { CalendarDays } from 'lucide-react'
import { formatarDataDigitada } from '@/lib/masks'
import styles from './CampoData.module.css'

interface Props {
  /** Valor em ISO (`aaaa-mm-dd`) — mesmo contrato do `<input type="date">` que este substitui. */
  value: string
  /** Devolve ISO (`aaaa-mm-dd`), ou string vazia enquanto a data estiver incompleta. */
  onChange: (iso: string) => void
  label?: string
  id?: string
  erro?: string
  /** Passado ao `<input type="date">` escondido, para o calendário respeitar o limite. */
  min?: string
  max?: string
  disabled?: boolean
  /** Some com o rótulo quando o campo já vive dentro de um bloco rotulado. */
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

/**
 * Campo de data com as duas coisas que os formulários precisavam e não tinham juntas.
 *
 * <p><b>O problema:</b> `<input type="date">` sozinho dá o calendário, mas exibe no idioma do
 * NAVEGADOR — quem tem o sistema em inglês vê `mm/dd/aaaa` e digita a data trocada. Texto
 * mascarado sozinho garante o `dd/mm/aaaa`, mas perde o calendário.
 *
 * <p><b>A solução:</b> o texto mascarado é o campo de verdade (sempre pt-BR, sempre digitável);
 * o `input[type=date]` fica escondido atrás do ícone e só serve para abrir o seletor nativo via
 * `showPicker()`. Quem prefere digitar, digita; quem prefere clicar, clica.
 *
 * <p><b>Fala ISO por fora, pt-BR por dentro.</b> O contrato é o mesmo do `<input type="date">`
 * que ele substitui — então virar drop-in em qualquer formulário não exige converter nada no
 * chamador, e é o formato que a API já espera. A exibição em pt-BR é assunto interno.
 */
export function CampoData({
  value, onChange, label, id, erro, min, max, disabled, semLabel,
}: Props) {
  const seletorRef = useRef<HTMLInputElement>(null)

  /**
   * O texto digitado vive aqui porque uma data pela metade ("12/0") não vira ISO nenhum.
   * Se o campo só espelhasse `value`, cada tecla que ainda não completa a data seria
   * descartada e a pessoa não conseguiria digitar.
   */
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

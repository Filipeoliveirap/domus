'use client'

import { useState } from 'react'
import { ArrowUp, ArrowDown, Minus } from 'lucide-react'
import type { VariacaoRelatorio } from '@/types/evento.type'
import styles from './CardVariacao.module.css'

interface Props {
  variacao: VariacaoRelatorio
  /** Rótulo do QUE está sendo comparado (ex.: "vs. anterior", "vs. média do filtro"). */
  rotulo: string
}

const TEXTO_BASE: Record<VariacaoRelatorio['base'], string> = {
  COMPARECIMENTO: 'Comparado por comparecimento real (os dois eventos controlam presença).',
  INSCRITOS: 'Comparado por inscritos confirmados (comparecimento indisponível em um dos eventos).',
}

/**
 * Badge de variação percentual — SEMPRE mostra a base usada (Decisão 4 do spec: nunca
 * implícito). `title` cobre o hover no desktop; o clique alterna um texto visível, que é o
 * que cobre o toque no mobile (onde não existe `:hover`/`title`).
 */
export function CardVariacao({ variacao, rotulo }: Props) {
  const [legendaAberta, setLegendaAberta] = useState(false)
  const positivo = variacao.percentual > 0
  const negativo = variacao.percentual < 0
  const classe = positivo ? styles.positivo : negativo ? styles.negativo : styles.neutro
  const Icone = positivo ? ArrowUp : negativo ? ArrowDown : Minus

  return (
    <span>
      <button
        type="button"
        className={`${styles.badge} ${classe}`}
        title={TEXTO_BASE[variacao.base]}
        onClick={() => setLegendaAberta((v) => !v)}
        aria-expanded={legendaAberta}
      >
        <Icone size={12} aria-hidden="true" />
        {Math.abs(variacao.percentual)}% {rotulo}
      </button>
      {legendaAberta && <span className={styles.legenda}>{TEXTO_BASE[variacao.base]}</span>}
    </span>
  )
}

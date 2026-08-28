'use client'

import { Check } from 'lucide-react'
import styles from './StepperPagamento.module.css'

export type EtapaPagamento = 'resumo' | 'pagamento' | 'confirmado'

const ETAPAS: { chave: EtapaPagamento; rotulo: string }[] = [
  { chave: 'resumo', rotulo: 'Resumo' },
  { chave: 'pagamento', rotulo: 'Pagamento' },
  { chave: 'confirmado', rotulo: 'Confirmação' },
]

interface Props {
  etapaAtual: EtapaPagamento
  /** Etapas visíveis — por padrão as 2 (sem "resumo"), pois a auto-inscrição individual é
   *  sempre "paga agora", sem card de divisão. O fluxo em lote passa as 3. */
  etapasVisiveis?: EtapaPagamento[]
}

export function StepperPagamento({ etapaAtual, etapasVisiveis = ['pagamento', 'confirmado'] }: Props) {
  const etapas = ETAPAS.filter((e) => etapasVisiveis.includes(e.chave))
  const indiceAtual = etapas.findIndex((e) => e.chave === etapaAtual)

  return (
    <ol className={styles.stepper} aria-label="Progresso do pagamento">
      {etapas.map((etapa, indice) => {
        const concluida = indice < indiceAtual
        const ativa = indice === indiceAtual
        return (
          <li
            key={etapa.chave}
            className={[styles.etapa, ativa ? styles.ativa : '', concluida ? styles.concluida : ''].join(' ')}
          >
            <span className={styles.bolinha} aria-hidden="true">
              {concluida ? <Check size={14} /> : indice + 1}
            </span>
            <span className={styles.rotulo}>{etapa.rotulo}</span>
          </li>
        )
      })}
    </ol>
  )
}

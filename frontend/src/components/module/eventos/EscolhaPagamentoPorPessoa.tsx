'use client'

import { useState } from 'react'
import { formatarMoeda } from '@/lib/formats/financeiro/movimentacaoFormat'
import { Button } from '@/components/common/button/Button'
import styles from './EscolhaPagamentoPorPessoa.module.css'

export type EscolhaPagamento = 'PAGAR_AGORA' | 'GERAR_LINK'

export interface PessoaACobrar {
  id: string
  nome: string
  valor: number
  ehTitular: boolean
}

interface Props {
  pessoas: PessoaACobrar[]
  onConfirmar: (escolhas: Record<string, EscolhaPagamento>) => void
  isLoading: boolean
}

/**
 * Card "Divisão de Pagamento" (Task 14) — decide, pessoa por pessoa, quem paga agora
 * (Payment Brick) e quem recebe um link (Mercado Pago) pra pagar depois.
 *
 * <p>Titular sempre fica travado em "eu pago agora" — regra de negócio fechada no
 * brainstorm e já reforçada no backend (Task 9: `criarParaTitular` nunca gera link):
 * evita uma inscrição inteira travada porque nem o titular resolveu o próprio pagamento.
 */
export function EscolhaPagamentoPorPessoa({ pessoas, onConfirmar, isLoading }: Props) {
  const [escolhas, setEscolhas] = useState<Record<string, EscolhaPagamento>>(
    Object.fromEntries(pessoas.map((p) => [p.id, 'PAGAR_AGORA']))
  )

  const totalAgora = pessoas
    .filter((p) => escolhas[p.id] === 'PAGAR_AGORA')
    .reduce((soma, p) => soma + p.valor, 0)

  function escolher(pessoaId: string, escolha: EscolhaPagamento) {
    setEscolhas((atual) => ({ ...atual, [pessoaId]: escolha }))
  }

  return (
    <div className={styles.wrapper}>
      <h3 className={styles.titulo}>Divisão de pagamento</h3>
      <p className={styles.subtitulo}>
        Como você deseja acertar as inscrições? Pague tudo agora ou envie links de
        cobrança individuais pra cada convidado.
      </p>

      <ul className={styles.lista}>
        {pessoas.map((pessoa) => (
          <li key={pessoa.id} className={styles.item}>
            <div className={styles.pessoaInfo}>
              <strong>{pessoa.nome}</strong>
              <span className={styles.valor}>{formatarMoeda(pessoa.valor)}</span>
            </div>
            {pessoa.ehTitular ? (
              <span className={styles.pagaAgoraFixo}>Paga agora</span>
            ) : (
              <div className={styles.opcoes} role="group" aria-label={`Forma de pagamento de ${pessoa.nome}`}>
                <button
                  type="button"
                  className={escolhas[pessoa.id] === 'PAGAR_AGORA' ? styles.opcaoAtiva : styles.opcao}
                  onClick={() => escolher(pessoa.id, 'PAGAR_AGORA')}
                  aria-pressed={escolhas[pessoa.id] === 'PAGAR_AGORA'}
                >
                  Eu pago agora
                </button>
                <button
                  type="button"
                  className={escolhas[pessoa.id] === 'GERAR_LINK' ? styles.opcaoAtiva : styles.opcao}
                  onClick={() => escolher(pessoa.id, 'GERAR_LINK')}
                  aria-pressed={escolhas[pessoa.id] === 'GERAR_LINK'}
                >
                  Enviar link
                </button>
              </div>
            )}
          </li>
        ))}
      </ul>

      <div className={styles.total}>
        <span>Total a pagar agora</span>
        <strong>{formatarMoeda(totalAgora)}</strong>
      </div>

      <Button
        type="button"
        variant="primary"
        size="md"
        isLoading={isLoading}
        onClick={() => onConfirmar(escolhas)}
      >
        Confirmar inscrição
      </Button>

      <p className={styles.seguranca}>
        Pagamento processado com segurança pelo Mercado Pago.
      </p>
    </div>
  )
}

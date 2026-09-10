'use client'

import type { OpcoesPagamentoResponse } from '@/types/api.types'
import { formatarMoeda } from '@/lib/formats/financeiro/movimentacaoFormat'
import { Transicao } from '@/components/common/Transicao/Transicao'
import { Loader } from '@/components/common/Loader/Loader'
import styles from './ResumoTaxaPagamento.module.css'

interface Props {
  /** Quando false (evento gratuito / sem inscrição), não renderiza. */
  ativo: boolean
  /** Valor (em reais) que a igreja quer receber por inscrição. `undefined`/0 = não renderiza. */
  preco: number | undefined
  aceitaCartao: boolean
  maxParcelas: number
  /** Opções calculadas pelo backend — o form é o dono da busca (uma só, sempre no máximo).
   *  `null` = ainda carregando (ou nada pra mostrar). */
  dados: OpcoesPagamentoResponse | null
  erro: boolean
}

/** Resumo estilo e-commerce mostrado ao GESTOR no cadastro de evento pago: quanto o
 *  pagador vai pagar em cada meio (a taxa do Mercado Pago é repassada via gross-up).
 *  A igreja recebe sempre o mesmo — o `preco` cheio. Não busca nada: renderiza o
 *  subconjunto das `dados` que corresponde à seleção atual de cartão/parcelas. */
export function ResumoTaxaPagamento({ ativo, preco, aceitaCartao, maxParcelas, dados, erro }: Props) {
  if (!ativo || !preco || preco <= 0) return null

  if (erro) {
    return (
      <div className={`card-painel ${styles.painel}`}>
        <p className={styles.erro}>Não foi possível calcular os valores agora.</p>
      </div>
    )
  }

  if (!dados) {
    return (
      <div className={`card-painel ${styles.painel}`}>
        <span className={styles.carregando}>
          <Loader variant="circular" size="lg" />
          <span>Calculando…</span>
        </span>
      </div>
    )
  }

  const pix = dados.opcoes.find((o) => o.meio === 'PIX')
  const cartao = aceitaCartao
    ? dados.opcoes.filter((o) => o.meio === 'CARTAO' && o.parcelas <= maxParcelas)
    : []
  // Assina o resultado atual — muda só quando um novo cálculo realmente chega (o debounce
  // do fetch no form já impede que isso re-anime a cada tecla). Trocar a `key` remonta o
  // bloco e re-dispara a animação de montagem do <Transicao>.
  const assinatura = `${dados.valorEvento}-${aceitaCartao ? maxParcelas : 0}-${cartao.map((o) => o.valorTotal).join(',')}`

  return (
    <div className={`card-painel ${styles.painel}`}>
      <p className={styles.titulo}>O pagador vai pagar</p>
      <Transicao key={assinatura} modo="fade">
        <ul className={styles.linhas}>
          {pix && (
            <li>
              <span>Pix</span>
              <span className={styles.valor}>{formatarMoeda(pix.valorTotal)}</span>
            </li>
          )}
          {cartao.map((o) => (
            <li key={o.parcelas}>
              <span>{o.parcelas === 1 ? 'Cartão à vista' : `Cartão em ${o.parcelas}x`}</span>
              <span className={styles.valor}>
                {formatarMoeda(o.valorTotal)}
                {o.parcelas > 1 && (
                  <em className={styles.parcela}> ({formatarMoeda(o.valorParcela)}/mês)</em>
                )}
              </span>
            </li>
          ))}
        </ul>
        <p className={styles.rodape}>
          A igreja recebe <strong>{formatarMoeda(dados.valorEvento)}</strong> em qualquer opção.
        </p>
      </Transicao>
    </div>
  )
}

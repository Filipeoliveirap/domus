'use client'

import { useEffect, useState } from 'react'
import { eventosService } from '@/services/evento.service'
import type { OpcoesPagamentoResponse } from '@/types/api.types'
import { formatarMoeda } from '@/lib/formats/financeiro/movimentacaoFormat'
import { Transicao } from '@/components/common/Transicao/Transicao'
import { Loader } from '@/components/common/Loader/Loader'
import styles from './ResumoTaxaPagamento.module.css'

interface Props {
  /** Quando false (evento gratuito / sem inscrição), não renderiza nem chama a API. */
  ativo: boolean
  /** Valor (em reais) que a igreja quer receber por inscrição. `undefined`/0 = não renderiza. */
  preco: number | undefined
  aceitaCartao: boolean
  maxParcelas: number
}

/** Resumo estilo e-commerce mostrado ao GESTOR no cadastro de evento pago: quanto o
 *  pagador vai pagar em cada meio (a taxa do Mercado Pago é repassada via gross-up).
 *  A igreja recebe sempre o mesmo — o `preco` cheio. */
export function ResumoTaxaPagamento({ ativo, preco, aceitaCartao, maxParcelas }: Props) {
  const [dados, setDados] = useState<OpcoesPagamentoResponse | null>(null)
  const [erro, setErro] = useState(false)

  useEffect(() => {
    if (!ativo || !preco || preco <= 0) {
      setDados(null)
      setErro(false)
      return
    }
    let vivo = true
    const t = setTimeout(() => {
      eventosService
        .simularPagamento({ preco, aceitaCartao, maxParcelas })
        .then((r) => { if (vivo) { setDados(r); setErro(false) } })
        .catch(() => { if (vivo) setErro(true) })
    }, 400) // debounce enquanto digita o preço — evita "pipoco" a cada tecla
    return () => { vivo = false; clearTimeout(t) }
  }, [ativo, preco, aceitaCartao, maxParcelas])

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
  const cartao = dados.opcoes.filter((o) => o.meio === 'CARTAO')
  // Assina o resultado atual — muda só quando um novo cálculo realmente chega (o debounce
  // do fetch já impede que isso re-anime a cada tecla). Trocar a `key` remonta o bloco e
  // re-dispara a animação de montagem do <Transicao>.
  const assinatura = `${dados.valorEvento}-${dados.opcoes.length}-${dados.opcoes.map((o) => o.valorTotal).join(',')}`

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

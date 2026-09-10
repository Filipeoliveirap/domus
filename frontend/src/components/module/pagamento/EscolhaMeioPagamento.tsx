'use client'

import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { CreditCard, QrCode } from 'lucide-react'
import { cobrancaService } from '@/services/cobranca.service'
import type { OpcaoPagamento } from '@/types/api.types'
import { formatarMoeda } from '@/lib/formats/financeiro/movimentacaoFormat'
import { Loader } from '@/components/common/Loader/Loader'
import { Colapsavel } from '@/components/common/Transicao/Colapsavel'
import styles from './EscolhaMeioPagamento.module.css'

interface Props {
  cobrancaId: string
  onEscolher: (opcao: OpcaoPagamento) => void
}

type MeioSel = 'PIX' | 'CARTAO' | null

/**
 * Mini "resumo de pedido" (irmão menor do bloco de detalhe do comprovante): mostra por que
 * o total é maior que o valor da inscrição — base + taxa de serviço, com o Total destacado.
 */
function ResumoPreco({
  base,
  taxa,
  total,
  parcelaLinha,
}: {
  base: number
  taxa: number
  total: number
  parcelaLinha?: string
}) {
  return (
    <div className={styles.resumoPreco}>
      <div className={styles.rpLinha}>
        <span className={styles.rpLabel}>Valor da inscrição</span>
        <span className={styles.rpValor}>{formatarMoeda(base)}</span>
      </div>
      <div className={styles.rpLinha}>
        <span className={styles.rpLabel}>Taxa de serviço</span>
        <span className={styles.rpValor}>{formatarMoeda(taxa)}</span>
      </div>
      {parcelaLinha && (
        <div className={styles.rpLinha}>
          <span className={styles.rpLabel}>Parcelamento</span>
          <span className={styles.rpValor}>{parcelaLinha}</span>
        </div>
      )}
      <div className={`${styles.rpLinha} ${styles.rpTotal}`}>
        <span className={styles.rpLabel}>Total</span>
        <span className={styles.rpValor}>{formatarMoeda(total)}</span>
      </div>
    </div>
  )
}

/**
 * Tela de escolha de meio de pagamento / parcelas, mostrada ANTES do Payment Brick.
 * O backend (`GET /cobrancas/{id}/opcoes-pagamento`) já devolve cada opção com a taxa do
 * Mercado Pago embutida em `valorTotal` — aqui só listamos e deixamos a pessoa escolher.
 *
 * UX (revisão 2026-09-09): dois cartões estilo rádio (Pix / Cartão). Escolher "Cartão"
 * revela as parcelas num `<Colapsavel>`. O contrato com o pai (`onEscolher`) é o mesmo —
 * uma `OpcaoPagamento` só; o Brick é travado nesse meio/parcelas depois.
 */
export function EscolhaMeioPagamento({ cobrancaId, onEscolher }: Props) {
  const { data, isLoading, isError } = useQuery({
    queryKey: ['opcoes-pagamento', cobrancaId],
    queryFn: () => cobrancaService.opcoesPagamento(cobrancaId),
    staleTime: 5 * 60_000,
  })

  const [meio, setMeio] = useState<MeioSel>(null)
  const [parcelasSel, setParcelasSel] = useState<number>(1)

  if (isLoading) {
    return (
      <div className={styles.centro} role="status" aria-live="polite">
        <Loader size="lg" />
        <p>Carregando formas de pagamento…</p>
      </div>
    )
  }

  if (isError || !data) {
    return <p className={styles.erro}>Não foi possível carregar as formas de pagamento. Recarregue a página e tente de novo.</p>
  }

  const pixOpcao = data.opcoes.find((o) => o.meio === 'PIX') ?? null
  const cartaoOpcoes = data.opcoes
    .filter((o) => o.meio === 'CARTAO')
    .sort((a, b) => a.parcelas - b.parcelas)
  const maxParcelas = cartaoOpcoes.reduce((m, o) => Math.max(m, o.parcelas), 0)
  // `parcelasSel` é só a intenção do clique; a opção realmente selecionada cai no 1º item
  // quando não existe faixa igual (ex.: nenhuma opção "1x"). O `aria-checked` das linhas e
  // o "Continuar" usam SEMPRE este objeto — nunca `parcelasSel` cru — pra não haver linha
  // marcada divergindo do que é enviado.
  const cartaoSelecionada =
    cartaoOpcoes.find((o) => o.parcelas === parcelasSel) ?? cartaoOpcoes[0] ?? null

  function teclaEscolhe(e: React.KeyboardEvent, acao: () => void) {
    if (e.key === 'Enter' || e.key === ' ') {
      e.preventDefault()
      acao()
    }
  }

  return (
    <div className={styles.wrapper}>
      <div className={styles.resumo}>
        <p className={styles.pergunta}>Como você quer pagar?</p>
        <p className={styles.valorEvento}>
          Valor da inscrição: <strong>{formatarMoeda(data.valorEvento)}</strong>
        </p>
        <p className={styles.aviso}>
          O total já inclui a taxa de pagamento, que varia conforme a forma escolhida.
        </p>
      </div>

      <div className={styles.cards} role="radiogroup" aria-label="Forma de pagamento">
        {pixOpcao && (
          <div
            role="radio"
            aria-checked={meio === 'PIX'}
            tabIndex={0}
            className={`${styles.card} ${meio === 'PIX' ? styles.cardAtivo : ''}`}
            style={{ animationDelay: '0.04s' }}
            onClick={() => setMeio('PIX')}
            onKeyDown={(e) => teclaEscolhe(e, () => setMeio('PIX'))}
          >
            <span className={styles.cardIcone}><QrCode size={22} aria-hidden="true" /></span>
            <span className={styles.cardTextos}>
              <span className={styles.cardLabel}>Pix</span>
              <span className={styles.cardDescricao}>Aprovação na hora · menor taxa</span>
            </span>
            <span className={styles.radio} aria-hidden="true" />
          </div>
        )}

        {cartaoOpcoes.length > 0 && (
          <div
            role="radio"
            aria-checked={meio === 'CARTAO'}
            tabIndex={0}
            className={`${styles.card} ${meio === 'CARTAO' ? styles.cardAtivo : ''}`}
            style={{ animationDelay: '0.1s' }}
            onClick={() => setMeio('CARTAO')}
            onKeyDown={(e) => teclaEscolhe(e, () => setMeio('CARTAO'))}
          >
            <span className={styles.cardIcone}><CreditCard size={22} aria-hidden="true" /></span>
            <span className={styles.cardTextos}>
              <span className={styles.cardLabel}>Cartão de crédito</span>
              <span className={styles.cardDescricao}>Em até {maxParcelas}x</span>
            </span>
            <span className={styles.radio} aria-hidden="true" />
          </div>
        )}
      </div>

      {pixOpcao && (
        <Colapsavel aberto={meio === 'PIX'}>
          <div className={styles.painel}>
            <ResumoPreco base={data.valorEvento} taxa={pixOpcao.taxa} total={pixOpcao.valorTotal} />
            <button type="button" className={styles.continuar} onClick={() => onEscolher(pixOpcao)}>
              Continuar
            </button>
          </div>
        </Colapsavel>
      )}

      {cartaoOpcoes.length > 0 && (
        <Colapsavel aberto={meio === 'CARTAO'}>
          <div className={styles.painel}>
            <div className={styles.parcelas} role="radiogroup" aria-label="Número de parcelas">
              {cartaoOpcoes.map((o) => (
                <div
                  key={o.parcelas}
                  role="radio"
                  aria-checked={cartaoSelecionada?.parcelas === o.parcelas}
                  tabIndex={0}
                  className={`${styles.parcela} ${cartaoSelecionada?.parcelas === o.parcelas ? styles.parcelaAtiva : ''}`}
                  onClick={() => setParcelasSel(o.parcelas)}
                  onKeyDown={(e) => teclaEscolhe(e, () => setParcelasSel(o.parcelas))}
                >
                  <span className={styles.parcelaEsq}>
                    {o.parcelas === 1 ? 'À vista' : `${o.parcelas}x de ${formatarMoeda(o.valorParcela)}`}
                  </span>
                  <span className={styles.parcelaDir}>{formatarMoeda(o.valorTotal)}</span>
                </div>
              ))}
            </div>
            {cartaoSelecionada && (
              <ResumoPreco
                base={data.valorEvento}
                taxa={cartaoSelecionada.taxa}
                total={cartaoSelecionada.valorTotal}
                parcelaLinha={
                  cartaoSelecionada.parcelas === 1
                    ? undefined
                    : `${cartaoSelecionada.parcelas}x de ${formatarMoeda(cartaoSelecionada.valorParcela)}`
                }
              />
            )}
            <button
              type="button"
              className={styles.continuar}
              disabled={!cartaoSelecionada}
              onClick={() => cartaoSelecionada && onEscolher(cartaoSelecionada)}
            >
              Continuar
            </button>
          </div>
        </Colapsavel>
      )}
    </div>
  )
}

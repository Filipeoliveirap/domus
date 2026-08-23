'use client'

import { use, useState } from 'react'
import { CalendarDays, CheckCircle2 } from 'lucide-react'
import { useCobrancaPublica } from '@/hooks/cobranca/useCobrancaPublica'
import { PaymentBrickCheckout } from '@/components/module/pagamento/PaymentBrickCheckout'
import { formatarMoeda } from '@/lib/formats/financeiro/movimentacaoFormat'
import styles from './CobrancaPublica.module.css'

/**
 * Página pública de cobrança individual (Task 13-14) — aberta sem sessão, a partir do
 * link compartilhado por `ModalCompartilharCobranca`. Layout inspirado em
 * `/convite/[token]` (mesmo header simples + card central), mas sem os dados de igreja
 * (o `CobrancaPublicaDTO` do backend não os expõe — deliberadamente enxuto, ver
 * `CobrancaController`).
 */
export default function CobrancaPublicaPage({ params }: { params: Promise<{ token: string }> }) {
  const { token } = use(params)
  const { data: cobranca, isLoading, isError } = useCobrancaPublica(token)
  const [pagamentoIniciado, setPagamentoIniciado] = useState(false)

  if (isLoading) {
    return <div className={styles.pagina}><p className={styles.estado}>Carregando…</p></div>
  }

  if (isError || !cobranca) {
    return (
      <div className={styles.pagina}>
        <div className={styles.card}>
          <h1>Link inválido</h1>
          <p>Este link de pagamento não existe ou expirou.</p>
        </div>
      </div>
    )
  }

  if (cobranca.status === 'PAGO' || pagamentoIniciado) {
    return (
      <div className={styles.pagina}>
        <div className={styles.card}>
          <CheckCircle2 size={40} className={styles.iconeSucesso} aria-hidden="true" />
          <h1>{pagamentoIniciado ? 'Pagamento em processamento' : 'Pagamento confirmado'}</h1>
          <p>
            {pagamentoIniciado
              ? 'Assim que o Mercado Pago confirmar, sua inscrição fica garantida. Isso costuma levar só alguns instantes.'
              : `Obrigado! Sua parte em "${cobranca.tituloEvento}" já está paga.`}
          </p>
        </div>
      </div>
    )
  }

  if (cobranca.status !== 'PENDENTE') {
    return (
      <div className={styles.pagina}>
        <div className={styles.card}>
          <h1>Link não disponível</h1>
          <p>Esta cobrança foi cancelada ou não está mais disponível para pagamento.</p>
        </div>
      </div>
    )
  }

  return (
    <div className={styles.pagina}>
      <header className={styles.topo}>
        <span className={styles.topoIcone}><CalendarDays size={18} /></span>
        <span>{cobranca.tituloEvento}</span>
      </header>

      <div className={styles.conteudo}>
        <div className={styles.card}>
          <p className={styles.saudacao}>{cobranca.nomePagador}, sua parte:</p>
          <p className={styles.valor}>{formatarMoeda(cobranca.valor)}</p>
        </div>

        <PaymentBrickCheckout
          cobrancaId={cobranca.id}
          valor={cobranca.valor}
          onPagamentoCriado={() => setPagamentoIniciado(true)}
        />

        <p className={styles.seguranca}>Pagamento processado com segurança pelo Mercado Pago.</p>
      </div>
    </div>
  )
}

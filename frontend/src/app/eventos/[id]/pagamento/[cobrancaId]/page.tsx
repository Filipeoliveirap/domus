'use client'

import { use, useState } from 'react'
import Link from 'next/link'
import { CalendarDays, CheckCircle2 } from 'lucide-react'
import { useCobrancaCheckout } from '@/hooks/cobranca/useCobrancaCheckout'
import { PaymentBrickCheckout } from '@/components/module/pagamento/PaymentBrickCheckout'
import { StepperPagamento } from '@/components/module/pagamento/StepperPagamento'
import { formatarMoeda } from '@/lib/formats/financeiro/movimentacaoFormat'
import styles from './PagamentoEvento.module.css'

function formatarDataEvento(iso: string): string {
  return new Date(iso).toLocaleDateString('pt-BR', {
    day: '2-digit', month: 'long', year: 'numeric', hour: '2-digit', minute: '2-digit',
  })
}

export default function PagamentoEventoPage({
  params,
}: {
  params: Promise<{ id: string; cobrancaId: string }>
}) {
  const { id: eventoId, cobrancaId } = use(params)
  const { data: cobranca, isLoading, isError } = useCobrancaCheckout(cobrancaId)
  const [confirmado, setConfirmado] = useState(false)

  if (isLoading) {
    return <div className={styles.pagina}><p className={styles.estado}>Carregando…</p></div>
  }

  if (isError || !cobranca) {
    return (
      <div className={styles.pagina}>
        <div className={styles.card}>
          <h1>Cobrança não encontrada</h1>
          <p>Este link de pagamento não existe ou não está mais disponível.</p>
        </div>
      </div>
    )
  }

  if (cobranca.status !== 'PENDENTE' && !confirmado) {
    return (
      <div className={styles.pagina}>
        <div className={styles.card}>
          <h1>Pagamento não disponível</h1>
          <p>Esta cobrança já foi paga, cancelada ou expirou.</p>
          <Link href={`/eventos/${eventoId}`} className={styles.voltarLink}>Voltar para o evento</Link>
        </div>
      </div>
    )
  }

  return (
    <div className={styles.pagina}>
      <header className={styles.topo}>
        <span className={styles.topoIcone}><CalendarDays size={18} /></span>
        <div className={styles.topoTextos}>
          <span className={styles.topoTitulo}>{cobranca.tituloEvento}</span>
          <span className={styles.topoData}>{formatarDataEvento(cobranca.inicioEmEvento)}</span>
        </div>
      </header>

      <div className={styles.conteudo}>
        <StepperPagamento etapaAtual={confirmado ? 'confirmado' : 'pagamento'} />

        {confirmado ? (
          <div className={styles.card}>
            <CheckCircle2 size={40} className={styles.iconeSucesso} aria-hidden="true" />
            <h1>Pagamento em processamento</h1>
            <p>Assim que o Mercado Pago confirmar, sua inscrição fica garantida. Isso costuma levar só alguns instantes.</p>
            <Link href={`/eventos/${eventoId}`} className={styles.voltarLink}>Voltar para o evento</Link>
          </div>
        ) : (
          <>
            <div className={styles.card}>
              <p className={styles.saudacao}>{cobranca.nomePagador}, sua parte:</p>
              <p className={styles.valor}>{formatarMoeda(cobranca.valor)}</p>
            </div>

            <PaymentBrickCheckout
              cobrancaId={cobranca.id}
              valor={cobranca.valor}
              onPagamentoCriado={() => setConfirmado(true)}
            />

            <p className={styles.seguranca}>Pagamento processado com segurança pelo Mercado Pago.</p>
          </>
        )}
      </div>
    </div>
  )
}

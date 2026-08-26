'use client'

import { use, useEffect, useRef, useState } from 'react'
import { CalendarDays, CheckCircle2, Clock, XCircle } from 'lucide-react'
import { useCobrancaPublica } from '@/hooks/cobranca/useCobrancaPublica'
import { cobrancaService } from '@/services/cobranca.service'
import { PaymentBrickCheckout } from '@/components/module/pagamento/PaymentBrickCheckout'
import { formatarMoeda } from '@/lib/formats/financeiro/movimentacaoFormat'
import styles from './CobrancaPublica.module.css'

// Mesma lógica de `/eventos/[id]/pagamento/[cobrancaId]`: a confirmação definitiva (PAGO)
// chega assíncrona, pelo webhook do Mercado Pago — sem poll, a tela ficava presa em
// "processando" pra sempre, mesmo depois do pagamento já ter sido confirmado de verdade.
type Resultado = 'enviado' | 'aprovado'

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
  const [resultado, setResultado] = useState<Resultado | null>(null)
  // Preenchido quando a cobrança em si não tem mais como ser paga (expirou, vagas
  // esgotadas, já foi paga/cancelada) — vindo tanto do backend na hora de pagar quanto do
  // poll abaixo.
  const [indisponivel, setIndisponivel] = useState<string | null>(null)
  const resolvidoRef = useRef(false)

  // Reload no meio de um pagamento em voo não pode voltar pro formulário — reenviar
  // esbarraria em COBRANCA_JA_EM_PROCESSAMENTO. Retoma direto em "confirmando".
  useEffect(() => {
    if (cobranca?.pagamentoEmAndamento && !resultado) setResultado('enviado')
  }, [cobranca, resultado])

  useEffect(() => {
    if (!cobranca || resultado !== 'enviado') return
    resolvidoRef.current = false

    const intervalo = setInterval(async () => {
      if (resolvidoRef.current) return
      try {
        const { status } = await cobrancaService.status(cobranca.id)
        if (status === 'PAGO') {
          resolvidoRef.current = true
          setResultado('aprovado')
        } else if (status === 'EXPIRADO' || status === 'CANCELADO') {
          resolvidoRef.current = true
          setIndisponivel(
            status === 'EXPIRADO'
              ? 'O prazo para pagar esta cobrança expirou antes do Mercado Pago confirmar.'
              : 'Esta cobrança foi cancelada.'
          )
        }
        // PENDENTE: continua esperando, não muda nada.
      } catch {
        // Falha de rede pontual no poll não é motivo pra desistir — tenta de novo no próximo tick.
      }
    }, 4000)

    return () => clearInterval(intervalo)
  }, [resultado, cobranca])

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

  if (cobranca.status === 'PAGO' || resultado === 'aprovado') {
    return (
      <div className={styles.pagina}>
        <div className={styles.cardAprovado}>
          <div className={styles.aneisAprovado}>
            <span className={styles.anelAprovado} aria-hidden="true" />
            <CheckCircle2 size={40} className={styles.iconeAprovado} aria-hidden="true" />
          </div>
          <h1 className={styles.aprovadoTitulo}>Pagamento confirmado</h1>
          <p className={styles.aprovadoTexto}>
            Obrigado! Sua parte em &quot;{cobranca.tituloEvento}&quot; já está paga.
          </p>
          <div className={styles.aprovadoResumo}>
            <span className={styles.aprovadoResumoLabel}>Valor da inscrição de {cobranca.nomePagador}</span>
            <span className={styles.aprovadoResumoValor}>{formatarMoeda(cobranca.valor)}</span>
          </div>
        </div>
      </div>
    )
  }

  if (indisponivel) {
    return (
      <div className={styles.pagina}>
        <div className={styles.card}>
          <XCircle size={40} className={styles.iconeErro} aria-hidden="true" />
          <h1>Pagamento não disponível</h1>
          <p>{indisponivel}</p>
          <p>Peça pra quem te convidou gerar um novo link.</p>
        </div>
      </div>
    )
  }

  if (resultado === 'enviado') {
    return (
      <div className={styles.pagina}>
        <div className={styles.card}>
          <Clock size={40} className={styles.iconeAguardando} aria-hidden="true" />
          <h1>Confirmando pagamento…</h1>
          <p>Assim que o Mercado Pago confirmar, sua inscrição fica garantida. Isso costuma levar só alguns instantes.</p>
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
          onPagamentoCriado={() => setResultado('enviado')}
          onCobrancaIndisponivel={setIndisponivel}
        />

        <p className={styles.seguranca}>Pagamento processado com segurança pelo Mercado Pago.</p>
      </div>
    </div>
  )
}

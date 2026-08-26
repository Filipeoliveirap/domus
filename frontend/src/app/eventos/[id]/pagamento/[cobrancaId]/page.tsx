'use client'

import { use, useEffect, useRef, useState } from 'react'
import Link from 'next/link'
import { CalendarDays, CheckCircle2, Clock, XCircle } from 'lucide-react'
import { useCobrancaCheckout } from '@/hooks/cobranca/useCobrancaCheckout'
import { cobrancaService } from '@/services/cobranca.service'
import { PaymentBrickCheckout } from '@/components/module/pagamento/PaymentBrickCheckout'
import { StepperPagamento } from '@/components/module/pagamento/StepperPagamento'
import { formatarMoeda } from '@/lib/formats/financeiro/movimentacaoFormat'
import styles from './PagamentoEvento.module.css'

function formatarDataEvento(iso: string): string {
  return new Date(iso).toLocaleDateString('pt-BR', {
    day: '2-digit', month: 'long', year: 'numeric', hour: '2-digit', minute: '2-digit',
  })
}

// A confirmação definitiva (PAGO) chega assíncrona, pelo webhook do Mercado Pago — não tem
// nenhum jeito do navegador saber na hora. 'enviado' é só "o pagamento foi criado, esperando
// o webhook confirmar"; 'aprovado' é o desfecho de sucesso que o poll abaixo resolve.
type Resultado = 'enviado' | 'aprovado'

export default function PagamentoEventoPage({
  params,
}: {
  params: Promise<{ id: string; cobrancaId: string }>
}) {
  const { id: eventoId, cobrancaId } = use(params)
  const { data: cobranca, isLoading, isError } = useCobrancaCheckout(cobrancaId)
  const [resultado, setResultado] = useState<Resultado | null>(null)
  // Preenchido quando a cobrança em si não tem mais como ser paga (expirou, vagas
  // esgotadas, já foi paga/cancelada) — vindo tanto do backend na hora de pagar quanto do
  // poll abaixo. Nenhum desses casos se resolve tentando de novo no mesmo formulário; a
  // mensagem já vem pronta (em português) de quem a disparou.
  const [indisponivel, setIndisponivel] = useState<string | null>(null)
  // Guarda contra o poll continuar rodando depois da resposta final (ou do componente
  // desmontar) — sem isto, um tick atrasado podia sobrescrever um estado já resolvido.
  const resolvidoRef = useRef(false)

  // Assim que o Mercado Pago recebe a tentativa de pagamento, pergunta a cada poucos
  // segundos se o webhook já confirmou — é a única forma de saber (o navegador não recebe
  // callback nenhum do lado do Mercado Pago). Some sozinho quando chega numa resposta final.
  useEffect(() => {
    if (!resultado || resultado !== 'enviado') return
    resolvidoRef.current = false

    const intervalo = setInterval(async () => {
      if (resolvidoRef.current) return
      try {
        const { status } = await cobrancaService.status(cobrancaId)
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
  }, [resultado, cobrancaId])

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

  if (cobranca.status !== 'PENDENTE' && !resultado) {
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
        <StepperPagamento etapaAtual={resultado || indisponivel ? 'confirmado' : 'pagamento'} />

        {resultado === 'aprovado' && (
          <div className={styles.cardAprovado}>
            <div className={styles.aneisAprovado}>
              <span className={styles.anelAprovado} aria-hidden="true" />
              <CheckCircle2 size={40} className={styles.iconeAprovado} aria-hidden="true" />
            </div>
            <h1 className={styles.aprovadoTitulo}>Pagamento aprovado!</h1>
            <p className={styles.aprovadoTexto}>
              Sua inscrição em &quot;{cobranca.tituloEvento}&quot; está confirmada.
            </p>
            <div className={styles.aprovadoResumo}>
              <span className={styles.aprovadoResumoLabel}>Valor pago por {cobranca.nomePagador}</span>
              <span className={styles.aprovadoResumoValor}>{formatarMoeda(cobranca.valor)}</span>
            </div>
            <Link href={`/eventos/${eventoId}`} className={styles.aprovadoAcao}>Voltar para o evento</Link>
          </div>
        )}

        {indisponivel && (
          <div className={styles.card}>
            <XCircle size={40} className={styles.iconeErro} aria-hidden="true" />
            <h1>Pagamento não disponível</h1>
            <p>{indisponivel}</p>
            <p>Volte pro evento e se inscreva de novo pra gerar uma nova cobrança.</p>
            <Link href={`/eventos/${eventoId}`} className={styles.voltarLink}>Voltar para o evento</Link>
          </div>
        )}

        {resultado === 'enviado' && !indisponivel && (
          <div className={styles.card}>
            <Clock size={40} className={styles.iconeAguardando} aria-hidden="true" />
            <h1>Confirmando pagamento…</h1>
            <p>Assim que o Mercado Pago confirmar, sua inscrição fica garantida. Isso costuma levar só alguns instantes.</p>
          </div>
        )}

        {!resultado && !indisponivel && (
          <>
            <div className={styles.card}>
              <p className={styles.saudacao}>Valor da inscrição de {cobranca.nomePagador}:</p>
              <p className={styles.valor}>{formatarMoeda(cobranca.valor)}</p>
            </div>

            <PaymentBrickCheckout
              cobrancaId={cobranca.id}
              valor={cobranca.valor}
              emailPagador={cobranca.emailPagador ?? undefined}
              onPagamentoCriado={() => setResultado('enviado')}
              onCobrancaIndisponivel={setIndisponivel}
            />

            <p className={styles.seguranca}>Pagamento processado com segurança pelo Mercado Pago.</p>
          </>
        )}
      </div>
    </div>
  )
}

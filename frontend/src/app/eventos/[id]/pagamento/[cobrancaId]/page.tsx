'use client'

import { use, useEffect, useRef, useState } from 'react'
import axios from 'axios'
import Link from 'next/link'
import { useQuery } from '@tanstack/react-query'
import { CalendarDays, CheckCircle2, Clock, XCircle } from 'lucide-react'
import { useCobrancaCheckout } from '@/hooks/cobranca/useCobrancaCheckout'
import { cobrancaService } from '@/services/cobranca.service'
import { authService } from '@/services/auth.service'
import { PaymentBrickCheckout } from '@/components/module/pagamento/PaymentBrickCheckout'
import { TelaPix } from '@/components/module/pagamento/TelaPix'
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

  // Esta página é usada tanto pelo titular logado (fluxo normal) quanto por convidado sem
  // cadastro pagando pelo link público (Plano 4b, sem sessão nenhuma) — "Voltar para o
  // evento" navegaria pra uma tela que exige login, então não pode aparecer pra quem não
  // tem sessão. Sessão própria (não o authStore global), mesmo motivo de /convite/[token]:
  // precisa funcionar sem AuthGuard.
  const { data: sessao, isFetched: sessaoVerificada } = useQuery({
    queryKey: ['pagamento-sessao-atual'],
    queryFn: () => authService.me({ semRedirect: true }).catch((erro: unknown) => {
      if (axios.isAxiosError(erro) && erro.response?.status === 401) return null
      throw erro
    }),
    retry: false,
  })

  const [resultado, setResultado] = useState<Resultado | null>(null)
  // Preenchido quando a cobrança em si não tem mais como ser paga (expirou, vagas
  // esgotadas, já foi paga/cancelada) — vindo tanto do backend na hora de pagar quanto do
  // poll abaixo. Nenhum desses casos se resolve tentando de novo no mesmo formulário; a
  // mensagem já vem pronta (em português) de quem a disparou.
  const [indisponivel, setIndisponivel] = useState<string | null>(null)
  // Guarda contra o poll continuar rodando depois da resposta final (ou do componente
  // desmontar) — sem isto, um tick atrasado podia sobrescrever um estado já resolvido.
  const resolvidoRef = useRef(false)

  // Reload no meio de um pagamento em voo (mpPaymentId já gravado, webhook ainda não
  // confirmou) não pode voltar pro formulário — reenviar esbarraria em
  // COBRANCA_JA_EM_PROCESSAMENTO. Retoma direto em "confirmando", que já dispara o poll abaixo.
  useEffect(() => {
    if (cobranca?.pagamentoEmAndamento && !resultado) setResultado('enviado')
  }, [cobranca, resultado])

  // Achado ao vivo (2026-08-27): reload/demora na tela do QR do Pix caía direto na tela
  // genérica "Confirmando pagamento…", sem nenhum jeito de voltar a ver o QR/copia-e-cola —
  // o dado só vinha na resposta de `pagar`, que não pode ser chamado de novo (cai em
  // COBRANCA_JA_EM_PROCESSAMENTO). Busca uma vez, junto com "confirmando"; nulos nos dois
  // campos = o pagamento em andamento é cartão, não Pix — mostra a tela genérica de sempre.
  const { data: pix } = useQuery({
    queryKey: ['cobranca-pix', cobrancaId],
    queryFn: () => cobrancaService.pix(cobrancaId),
    enabled: resultado === 'enviado',
    retry: false,
  })

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
          <Link href={`/eventos?detalhe=${eventoId}`} className={styles.voltarLink}>Voltar para o evento</Link>
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
              <span className={styles.aprovadoResumoLabel}>Valor da inscrição de {cobranca.nomePagador}</span>
              <span className={styles.aprovadoResumoValor}>{formatarMoeda(cobranca.valor)}</span>
            </div>
            {sessaoVerificada && sessao ? (
              <Link href={`/eventos?detalhe=${eventoId}`} className={styles.aprovadoAcao}>Voltar para o evento</Link>
            ) : (
              sessaoVerificada && <p className={styles.aprovadoFechar}>Já pode fechar esta página.</p>
            )}
          </div>
        )}

        {indisponivel && (
          <div className={styles.card}>
            <XCircle size={40} className={styles.iconeErro} aria-hidden="true" />
            <h1>Pagamento não disponível</h1>
            <p>{indisponivel}</p>
            <p>Volte pro evento e se inscreva de novo pra gerar uma nova cobrança.</p>
            <Link href={`/eventos?detalhe=${eventoId}`} className={styles.voltarLink}>Voltar para o evento</Link>
          </div>
        )}

        {resultado === 'enviado' && !indisponivel && (
          pix?.qrCode && pix?.qrCodeBase64 ? (
            <div className={styles.card}>
              <TelaPix qrCode={pix.qrCode} qrCodeBase64={pix.qrCodeBase64} />
            </div>
          ) : (
            <div className={styles.card}>
              <Clock size={40} className={styles.iconeAguardando} aria-hidden="true" />
              <h1>Confirmando pagamento…</h1>
              <p>Assim que o Mercado Pago confirmar, sua inscrição fica garantida. Isso costuma levar só alguns instantes.</p>
            </div>
          )
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

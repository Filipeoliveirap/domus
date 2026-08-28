'use client'

import { use, useEffect, useRef, useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { CalendarDays, CheckCircle2, Clock, XCircle } from 'lucide-react'
import { useCobrancaPublica } from '@/hooks/cobranca/useCobrancaPublica'
import { cobrancaService } from '@/services/cobranca.service'
import { PaymentBrickCheckout } from '@/components/module/pagamento/PaymentBrickCheckout'
import { TelaPix } from '@/components/module/pagamento/TelaPix'
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
  // Achado ao vivo (2026-08-27): abrir um link de cobrança diferente troca só o token da
  // rota — o Next reaproveita a MESMA instância desta página. `key` força remontar o
  // conteúdo inteiro a cada token diferente, resetando `resultado`/`indisponivel`/o `pix`
  // do PaymentBrickCheckout — sem isto, o estado do link anterior vazava pro novo.
  return <ConteudoCobranca key={token} token={token} />
}

function ConteudoCobranca({ token }: { token: string }) {
  const queryClient = useQueryClient()
  const { data: cobranca, isLoading, isError } = useCobrancaPublica(token)
  const [resultado, setResultado] = useState<Resultado | null>(null)
  const [reiniciando, setReiniciando] = useState(false)
  // Preenchido quando a cobrança em si não tem mais como ser paga (expirou, vagas
  // esgotadas, já foi paga/cancelada) — vindo tanto do backend na hora de pagar quanto do
  // poll abaixo.
  const [indisponivel, setIndisponivel] = useState<string | null>(null)
  const resolvidoRef = useRef(false)

  // Reload no meio de um pagamento em voo não pode voltar pro formulário — reenviar
  // esbarraria em COBRANCA_JA_EM_PROCESSAMENTO. Retoma direto em "confirmando". Derivado
  // em vez de sincronizado via effect (setState síncrono no corpo do effect é o padrão que
  // causava o vazamento de estado do parágrafo acima).
  const resultadoEfetivo: Resultado | null = resultado ?? (cobranca?.pagamentoEmAndamento ? 'enviado' : null)

  // Mesmo achado da tela irmã (`/eventos/[id]/pagamento/[cobrancaId]`, 2026-08-27): reload
  // no meio do Pix caía direto em "confirmando" sem jeito de voltar a ver o QR.
  const { data: pix } = useQuery({
    queryKey: ['cobranca-pix', cobranca?.id],
    queryFn: () => cobrancaService.pix(cobranca!.id),
    enabled: !!cobranca && resultadoEfetivo === 'enviado',
    retry: false,
  })

  useEffect(() => {
    if (!cobranca || resultadoEfetivo !== 'enviado') return
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
  }, [resultadoEfetivo, cobranca])

  // "QR Code não funcionou / pagar de outro jeito" (achado ao vivo, 2026-08-27): mesma
  // lógica da tela irmã (`/eventos/[id]/pagamento/[cobrancaId]`).
  async function aoReiniciar() {
    if (!cobranca) return
    setReiniciando(true)
    try {
      await cobrancaService.reiniciar(cobranca.id)
      await queryClient.invalidateQueries({ queryKey: ['cobranca-publica', token] })
      setResultado(null)
    } finally {
      setReiniciando(false)
    }
  }

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

  if (cobranca.status === 'PAGO' || resultadoEfetivo === 'aprovado') {
    return (
      <div className={styles.pagina}>
        <div className={styles.cardAprovado}>
          <div className={styles.aneisAprovado}>
            <span className={styles.anelAprovado} aria-hidden="true" />
            <CheckCircle2 size={40} className={styles.iconeAprovado} aria-hidden="true" />
          </div>
          <h1 className={styles.aprovadoTitulo}>Pagamento confirmado</h1>
          <p className={styles.aprovadoTexto}>
            Obrigado! Sua inscrição em &quot;{cobranca.tituloEvento}&quot; está paga.
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

  if (resultadoEfetivo === 'enviado') {
    return (
      <div className={styles.pagina}>
        {pix?.qrCode && pix?.qrCodeBase64 ? (
          <div className={styles.card}>
            <TelaPix
              qrCode={pix.qrCode}
              qrCodeBase64={pix.qrCodeBase64}
              expiraEm={pix.expiraEm ?? cobranca.expiraEm}
              onReiniciar={aoReiniciar}
              reiniciando={reiniciando}
            />
          </div>
        ) : (
          <div className={styles.card}>
            <Clock size={40} className={styles.iconeAguardando} aria-hidden="true" />
            <h1>Confirmando pagamento…</h1>
            <p>Assim que o Mercado Pago confirmar, sua inscrição fica garantida. Isso costuma levar só alguns instantes.</p>
          </div>
        )}
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
          key={cobranca.id}
          cobrancaId={cobranca.id}
          valor={cobranca.valor}
          expiraEm={cobranca.expiraEm}
          onPagamentoCriado={() => setResultado('enviado')}
          onCobrancaIndisponivel={setIndisponivel}
        />

        <p className={styles.seguranca}>Pagamento processado com segurança pelo Mercado Pago.</p>
      </div>
    </div>
  )
}

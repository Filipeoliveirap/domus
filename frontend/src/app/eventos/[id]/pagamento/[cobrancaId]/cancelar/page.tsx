'use client'

import { use, useState } from 'react'
import axios from 'axios'
import Link from 'next/link'
import { useQuery } from '@tanstack/react-query'
import { CalendarDays, AlertTriangle, CheckCircle2 } from 'lucide-react'
import { useCobrancaCheckout } from '@/hooks/cobranca/useCobrancaCheckout'
import { cobrancaService } from '@/services/cobranca.service'
import { authService } from '@/services/auth.service'
import { Button } from '@/components/common/button/Button'
import { formatarMoeda } from '@/lib/formats/financeiro/movimentacaoFormat'
import styles from '../PagamentoEvento.module.css'

/**
 * Confirmação de "Cancelar inscrição" — link do e-mail de lembrete de pagamento pendente.
 * Página própria (em vez de cancelar direto no clique do link) de propósito: e-mail client
 * e antivírus costumam pré-carregar/pré-clicar links pra checar segurança — um `POST`
 * disparado sem confirmação cancelaria inscrições sozinho. Pública, sem sessão, mesma
 * garantia de posse do resto do módulo de cobrança (o `id` já prova posse).
 */
export default function CancelarInscricaoPage({
  params,
}: {
  params: Promise<{ id: string; cobrancaId: string }>
}) {
  const { id: eventoId, cobrancaId } = use(params)
  const { data: cobranca, isLoading, isError } = useCobrancaCheckout(cobrancaId)
  const [cancelando, setCancelando] = useState(false)
  const [cancelado, setCancelado] = useState(false)
  const [erro, setErro] = useState<string | null>(null)

  // Mesmo motivo de /eventos/[id]/pagamento/[cobrancaId]: essa página também é aberta por
  // convidado sem cadastro (link do e-mail de lembrete, sem sessão nenhuma) — o botão
  // "Voltar para o evento" navegaria pra uma tela que exige login, então só aparece pra
  // quem realmente tem sessão. Sessão própria (não o authStore global), funciona sem AuthGuard.
  const { data: sessao, isFetched: sessaoVerificada } = useQuery({
    queryKey: ['pagamento-sessao-atual'],
    queryFn: () => authService.me({ semRedirect: true }).catch((erro: unknown) => {
      if (axios.isAxiosError(erro) && erro.response?.status === 401) return null
      throw erro
    }),
    retry: false,
  })
  const temSessao = sessaoVerificada && !!sessao

  async function aoConfirmar() {
    setCancelando(true)
    setErro(null)
    try {
      await cobrancaService.cancelarInscricao(cobrancaId)
      setCancelado(true)
    } catch {
      setErro('Não foi possível cancelar a inscrição. O link pode ter expirado, ou o pagamento já foi feito.')
    } finally {
      setCancelando(false)
    }
  }

  if (isLoading) {
    return <div className={styles.pagina}><p className={styles.estado}>Carregando…</p></div>
  }

  if (isError || !cobranca) {
    return (
      <div className={styles.pagina}>
        <div className={styles.conteudo}>
          <div className={styles.card}>
            <h1>Link inválido</h1>
            <p>Este link de cancelamento não existe ou não está mais disponível.</p>
          </div>
        </div>
      </div>
    )
  }

  if (cancelado) {
    return (
      <div className={styles.pagina}>
        <div className={styles.conteudo}>
          <div className={styles.cardAprovado}>
            <div className={styles.aneisAprovado}>
              <span className={styles.anelAprovado} aria-hidden="true" />
              <CheckCircle2 size={40} className={styles.iconeAprovado} aria-hidden="true" />
            </div>
            <h1 className={styles.aprovadoTitulo}>Inscrição cancelada</h1>
            <p className={styles.aprovadoTexto}>
              A sua inscrição em &quot;{cobranca.tituloEvento}&quot; foi cancelada.
            </p>
            {temSessao ? (
              <Link href={`/eventos?detalhe=${eventoId}`} className={styles.aprovadoAcao}>Voltar para o evento</Link>
            ) : (
              sessaoVerificada && <p className={styles.aprovadoFechar}>Já pode fechar esta página.</p>
            )}
          </div>
        </div>
      </div>
    )
  }

  if (cobranca.status !== 'PENDENTE') {
    return (
      <div className={styles.pagina}>
        <div className={styles.conteudo}>
          <div className={styles.card}>
            <h1>Nada para cancelar</h1>
            <p>Esta inscrição já foi paga, cancelada, ou não está mais aguardando pagamento.</p>
            {temSessao && (
              <Link href={`/eventos?detalhe=${eventoId}`} className={styles.voltarLink}>Voltar para o evento</Link>
            )}
          </div>
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
        </div>
      </header>

      <div className={styles.conteudo}>
        <div className={styles.card}>
          <AlertTriangle size={40} className={styles.iconeErro} aria-hidden="true" />
          <h1>Cancelar inscrição?</h1>
          <p>
            A sua inscrição em &quot;{cobranca.tituloEvento}&quot; ({formatarMoeda(cobranca.valor)})
            {' '}ainda está aguardando pagamento.
          </p>
          <p>Se cancelar, a vaga é liberada e não dá mais pra pagar por este link.</p>
          {erro && <p className={styles.avisoErro}>{erro}</p>}
          <div className={styles.acoesCancelar}>
            <Button type="button" variant="primary" size="md" isLoading={cancelando} onClick={aoConfirmar} style={{ width: '100%' }}>
              Cancelar inscrição
            </Button>
            {temSessao && (
              <Link href={`/eventos?detalhe=${eventoId}`} className={styles.voltarLink}>Voltar sem cancelar</Link>
            )}
          </div>
        </div>
      </div>
    </div>
  )
}

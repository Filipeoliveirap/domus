'use client'

import { useRef, useState } from 'react'
import Link from 'next/link'
import { useRouter } from 'next/navigation'
import { CheckCircle2, XCircle, ThumbsUp, AlertTriangle, Clock } from 'lucide-react'
import { useMinhaInscricao } from '@/hooks/inscricao/useMinhaInscricao'
import { useInscrever } from '@/hooks/inscricao/useInscrever'
import { useCancelarInscricao } from '@/hooks/inscricao/useCancelarInscricao'
import { useElegibilidade } from '@/hooks/inscricao/useElegibilidade'
import { useContaPagamento } from '@/hooks/pagamento/useContaPagamento'
import { useUiStore } from '@/store/uiStore'
import { useMinhaPessoa } from '@/hooks/pessoa/useMinhaPessoa'
import { useDefinirEmailInicial } from '@/hooks/pessoa/useDefinirEmailInicial'
import { useCamposPersonalizados } from '@/hooks/evento/useCamposPersonalizados'
import { useResponderCampos } from '@/hooks/inscricao/useResponderCampos'
import { ConfirmarCancelamentoInscricao } from './ConfirmarCancelamentoInscricao'
import { ModalCompletarDadosInscricao } from './ModalCompletarDadosInscricao'
import { ModalConfirmacao } from '@/components/common/ModalConfirmacao/ModalConfirmacao'
import { notificar } from '@/components/common/Notificacao/notificar'
import { useAuthStore } from '@/store/authStore'
import { podeGerenciarInscricoes } from '@/lib/permissoes'
import { podeCancelarInscricao } from '@/lib/formats/eventoFormat'
import { rotuloRole } from '@/lib/formats/usuarioFormat'
import { Transicao } from '@/components/common/Transicao/Transicao'
import { TrocaCena } from '@/components/common/TrocaCena/TrocaCena'
import type { SituacaoEvento, SituacaoInscricao, PoliticaCancelamentoAposPrazo } from '@/types/evento.type'
import type { Impedimento, MinhaInscricaoResponse } from '@/types/inscricao.type'
import styles from './BotaoConfirmarPresenca.module.css'

/** Fora do corpo do componente: `new Date(...)` é impuro pro react-hooks/purity. */
function formatarDiaMes(iso: string | null): string {
  return iso
    ? new Date(iso).toLocaleDateString('pt-BR', { day: '2-digit', month: '2-digit' })
    : ''
}

interface Props {
  eventoId: string
  inicioEm: string
  vagasRestantes: number | null
  requerInscricao: boolean
  situacao: SituacaoEvento
  situacaoInscricao: SituacaoInscricao
  inscricoesAte: string | null
  preco?: number | null
  politicaCancelamentoAposPrazo: PoliticaCancelamentoAposPrazo
  /** Chamado só quando a inscrição exige confirmação prévia (requerInscricao) e deu certo,
   *  SEM pagamento pendente — evento pago com sucesso navega pra rota de checkout em vez
   *  de chamar isto (o drawer não teria o que abrir; a pessoa já saiu da tela). */
  onInscritoComSucesso?: () => void
  /** Evento pago: chamado no instante em que a navegação pro checkout começa, atrás da
   *  ponte de transição — o drawer/modal usa pra animar a própria saída antes do route push. */
  onAntesDeNavegar?: () => void
}

export function BotaoConfirmarPresenca({
  eventoId, inicioEm, vagasRestantes, requerInscricao, situacao, situacaoInscricao,
  inscricoesAte, preco, politicaCancelamentoAposPrazo, onInscritoComSucesso, onAntesDeNavegar,
}: Props) {
  const router = useRouter()
  const abrirPonteCheckout = useUiStore((s) => s.abrirPonteCheckout)
  const fecharPonteCheckout = useUiStore((s) => s.fecharPonteCheckout)

  // Evento pago: leva pro checkout com uma "ponte" — mostra o selo "Inscrição feita!",
  // deixa o drawer/modal animar a saída atrás do vidro fosco (~0,5s) e só então faz o route
  // push, que troca pra uma rota full-screen fora do app shell. A ponte vive no uiStore
  // porque este componente desmonta junto com o drawer no meio da transição.
  function irParaCheckout(cobrancaId: string) {
    setNavegandoParaCheckout(true)
    abrirPonteCheckout()
    onAntesDeNavegar?.()
    window.setTimeout(() => {
      router.push(`/eventos/${eventoId}/pagamento/${cobrancaId}`)
    }, 550)
    // Segurança: se a navegação não acontecer (erro), não deixa o véu preso.
    window.setTimeout(fecharPonteCheckout, 5000)
  }
  const [confirmandoCancelamento, setConfirmandoCancelamento] = useState(false)
  const [semConta, setSemConta] = useState(false)
  // A mutation já resolveu (isPending vira false) antes do router.push completar a
  // navegação — sem isto, o botão "pisca" de volta pro texto normal por um instante
  // enquanto a rota de checkout ainda está carregando.
  const [navegandoParaCheckout, setNavegandoParaCheckout] = useState(false)
  // 422 contornável: gestor quebrando recorte de elegibilidade
  const [impedimentosParaConfirmar, setImpedimentosParaConfirmar] = useState<Impedimento[] | null>(null)
  // E-mail obrigatório (2026-08-27) + campos personalizados: se faltar algo, abre este
  // modal ANTES de inscrever — a ação real (toggle "Eu vou" ou "Se inscrever" com/sem
  // preço) fica guardada em `aposCompletarDadosRef`, chamada só depois de confirmar.
  const [completandoDados, setCompletandoDados] = useState(false)
  const aposCompletarDadosRef = useRef<() => void>(() => {})

  const role = useAuthStore((s) => s.role)
  // Gestor ignora restrições com confirmação extra
  const ehGestor = podeGerenciarInscricoes(role)

  // Prazo de inscrição (Task 11): gestor "fura" o prazo, comum é barrado.
  const podeFurarPrazo = ehGestor
  const encerradoPorPrazo = situacaoInscricao === 'ENCERRADA_POR_PRAZO'
  const dataPrazo = formatarDiaMes(inscricoesAte)

  // Task 11: membro comum não pode cancelar depois do prazo quando a política é NAO_PERMITIDO.
  const cancelamentoTravadoPorPrazo =
    situacaoInscricao === 'ENCERRADA_POR_PRAZO'
    && politicaCancelamentoAposPrazo === 'NAO_PERMITIDO'
    && !podeGerenciarInscricoes(role)

  const { data: minha, isLoading } = useMinhaInscricao(eventoId)
  // Status da conta MP da própria igreja — só importa quando o evento é pago.
  const { data: contaPagamento } = useContaPagamento()
  const { data: minhaPessoa } = useMinhaPessoa()
  const { data: camposPersonalizados } = useCamposPersonalizados(eventoId)
  const campos = camposPersonalizados ?? []
  const precisaCompletarDados = !minhaPessoa?.email || campos.length > 0
  const definirEmail = useDefinirEmailInicial()
  const { responder } = useResponderCampos()

  // Ação da própria pessoa: nunca notifica — o resultado já está na tela (o botão vira
  // "Inscrito" / volta pra "Se inscrever", com animação). Toast só pros erros (dentro do
  // hook). Evento pago mostra o próprio feedback na rota de checkout.
  const inscrever = useInscrever(eventoId, true, {
    onContornavel: ehGestor ? (imps) => setImpedimentosParaConfirmar(imps) : undefined,
  })
  const cancelar = useCancelarInscricao(true)

  // Gestor vê o motivo, mas o botão segue ativo (422 abre confirmação)
  const { data: elegibilidade } = useElegibilidade(eventoId)
  const impedimentoPreview = !elegibilidade?.apto ? elegibilidade?.impedimentos[0]?.mensagem : undefined
  const impedimento = ehGestor ? undefined : impedimentoPreview

  const eventoEncerrado = new Date(inicioEm) < new Date()
  const semVagas = vagasRestantes !== null && vagasRestantes <= 0
  const inscricaoBloqueadaPelaSituacao = situacao !== 'AGENDADO'

  // Ponto único que decide se pode inscrever direto ou precisa abrir o modal de dados
  // antes. `acao` é a inscrição de verdade (o que cada botão já fazia), guardada pra
  // rodar depois que o modal confirmar (ou direto, se não faltar nada).
  function tentarInscrever(acao: () => void) {
    if (precisaCompletarDados) {
      aposCompletarDadosRef.current = acao
      setCompletandoDados(true)
      return
    }
    acao()
  }

  const respostasPendentesRef = useRef<Record<string, string>>({})

  async function aoConfirmarDadosCompletos({ email, respostas }: { email: string | null; respostas: Record<string, string> }) {
    try {
      if (email && minhaPessoa) {
        await definirEmail.mutateAsync({ pessoaId: minhaPessoa.id, email })
      }
    } catch {
      notificar.erro('Não foi possível salvar o e-mail', 'Confira o e-mail e tente novamente.')
      return
    }
    // Guarda as respostas pra responder assim que a inscrição existir (inscricaoId só
    // nasce depois) — a própria ação (toggle/se inscrever) chama isto no onSuccess dela.
    respostasPendentesRef.current = respostas
    aposCompletarDadosRef.current()
  }

  async function aoInscreverComSucesso(resposta: MinhaInscricaoResponse) {
    const respostas = respostasPendentesRef.current
    respostasPendentesRef.current = {}
    if (resposta.id && Object.keys(respostas).length > 0) {
      const dados = campos.map((c) => ({ campoId: c.id, valor: respostas[c.id] ?? '' }))
      await responder(resposta.id, dados)
    }
    setCompletandoDados(false)
  }

  const modalCompletarDados = completandoDados && (
    <ModalCompletarDadosInscricao
      pedeEmail={!minhaPessoa?.email}
      campos={campos}
      isLoading={definirEmail.isPending || inscrever.isPending}
      onConfirmar={aoConfirmarDadosCompletos}
      onClose={() => setCompletandoDados(false)}
      onFecharTudo={() => setCompletandoDados(false)}
    />
  )

  const modalContorno = impedimentosParaConfirmar && (
    <ModalConfirmacao
      titulo="Inscrever mesmo assim?"
      textoConfirmar="Inscrever mesmo assim"
      isLoading={inscrever.isPending}
      onConfirmar={() => inscrever.mutate({ confirmado: true }, {
        onSuccess: async (resposta) => {
          setImpedimentosParaConfirmar(null)
          await aoInscreverComSucesso(resposta)
          if (resposta.cobrancaPendenteId) {
            irParaCheckout(resposta.cobrancaPendenteId)
          } else {
            onInscritoComSucesso?.()
          }
        },
      })}
      onClose={() => setImpedimentosParaConfirmar(null)}
      mensagem={
        <>
          <p>Você não atende a todos os requisitos deste evento:</p>
          <ul>
            {impedimentosParaConfirmar.map((imp) => (
              <li key={imp.codigo}>{imp.mensagem}</li>
            ))}
          </ul>
        </>
      }
    />
  )

  if (isLoading) {
    return (
      <button type="button" className={styles.botao} disabled>
        Carregando…
      </button>
    )
  }

  // Prazo encerrado + já inscrito não bloqueia (segue podendo ver/cancelar); só barra
  // quem ainda não entrou e não pode furar o prazo.
  const avisoPrazoGestor = encerradoPorPrazo && podeFurarPrazo && (
    <Transicao modo="fade">
      <p className={styles.avisoPrazo}>
        O prazo de inscrição encerrou em {dataPrazo}, mas você como{' '}
        {(rotuloRole(role ?? '') || 'gestor').toLowerCase()} pode inscrever assim mesmo.
      </p>
    </Transicao>
  )

  if (encerradoPorPrazo && !podeFurarPrazo && !minha?.inscrito) {
    return (
      <button type="button" className={styles.botao} disabled>
        Inscrições encerradas em {dataPrazo}
      </button>
    )
  }

  // Modo "Eu vou": alterna direto, sem diálogo de confirmação
  if (!requerInscricao) {
    const marcado = !!minha?.inscrito

    if (!marcado && inscricaoBloqueadaPelaSituacao) return null

    // Fora de AGENDADO: backend recusa cancelar
    if (marcado && !podeCancelarInscricao(situacao)) {
      return (
        <span className={styles.participou}>
          <CheckCircle2 size={15} aria-hidden="true" />
          Você participou deste evento
        </span>
      )
    }

    const pendente = inscrever.isPending || cancelar.isPending
    // Cancelamento não esbarra em elegibilidade
    const bloqueadoPorImpedimento = !marcado && !!impedimento

    function aoClicarEuVou() {
      if (marcado) {
        if (!minha?.id) return
        cancelar.mutate(minha.id)
      } else {
        tentarInscrever(() => inscrever.mutate({}, { onSuccess: aoInscreverComSucesso }))
      }
    }

    return (
      <span className={styles.euVouWrap}>
        {!marcado && avisoPrazoGestor}
        <button
          type="button"
          className={`${styles.euVou} ${marcado ? styles.euVouAtivo : ''}`}
          onClick={aoClicarEuVou}
          disabled={pendente || bloqueadoPorImpedimento}
          aria-pressed={marcado}
        >
          <span className={styles.euVouIcone}>
            <ThumbsUp size={15} className={styles.icone} aria-hidden="true" />
            {/* Faíscas: montam junto com o estado "vai" e disparam a animação uma vez —
                curtida de post. Somem ao desmarcar. */}
            {marcado && (
              <span className={styles.faiscas} aria-hidden="true">
                {[0, 1, 2, 3, 4, 5].map((i) => (
                  <i key={i} style={{ '--a': `${i * 60}deg` } as React.CSSProperties} />
                ))}
              </span>
            )}
          </span>
          {marcado ? 'Você vai' : 'Eu vou'}
        </button>
        {bloqueadoPorImpedimento && (
          <span className={styles.motivo}>
            <AlertTriangle size={13} aria-hidden="true" />
            {impedimento}
          </span>
        )}
        {modalContorno}
        {modalCompletarDados}
      </span>
    )
  }

  // A área de ação troca entre "inscrito", "pagamento pendente" e "se inscrever". Antes
  // cada estado era um `return` seco — o bloco antigo sumia na hora e o novo pipocava.
  // <TrocaCena> anima essas trocas (crossfade + altura acompanhando o conteúdo novo).
  // Pagamento pendente: inscrição AGUARDANDO_PAGAMENTO, vinda de dado do servidor (sobrevive
  // a reload) — cobre evento que virou pago, preço aumentado, ou checkout não terminado.
  const cenaAcao: 'inscrito' | 'pendente' | 'esgotado' | 'seinscrever' | null =
    minha?.inscrito
      ? 'inscrito'
      : minha?.cobrancaPendenteId
        ? 'pendente'
        : inscricaoBloqueadaPelaSituacao || eventoEncerrado
          ? null
          : semVagas
            ? 'esgotado'
            : 'seinscrever'

  if (cenaAcao === null) return null

  const podeCancelar = podeCancelarInscricao(situacao) && !cancelamentoTravadoPorPrazo
  const inscricaoId = minha?.id

  function inscreverDeVerdade() {
    if (!preco) {
      inscrever.mutate({}, {
        onSuccess: async (resposta) => {
          await aoInscreverComSucesso(resposta)
          onInscritoComSucesso?.()
        },
      })
      return
    }
    // Sem conta MP conectada, a rota de checkout nem carregaria (não há pra quem
    // receber) — aviso com atalho em vez de navegar pra uma tela que ia falhar.
    if (!contaPagamento?.conectada) {
      setSemConta(true)
      return
    }
    inscrever.mutate({}, {
      onSuccess: async (resposta) => {
        await aoInscreverComSucesso(resposta)
        if (resposta.cobrancaPendenteId) {
          irParaCheckout(resposta.cobrancaPendenteId)
        } else {
          // Não deveria acontecer (evento tem preço), mas não trava a pessoa numa tela morta.
          onInscritoComSucesso?.()
        }
      },
    })
  }

  return (
    <>
      <TrocaCena
        cenaKey={cenaAcao}
        renderCena={(cena) =>
          cena === 'inscrito' ? (
            <div className={styles.inscrito}>
              <div className={styles.inscritoStatus}>
                <CheckCircle2 size={18} aria-hidden="true" />
                <div className={styles.inscritoTexto}>
                  <strong>Inscrito</strong>
                  <span>{podeCancelar ? 'Tudo certo pra você!' : 'Você participou deste evento'}</span>
                </div>
              </div>

              {podeCancelar && (
                <button
                  type="button"
                  className={styles.cancelarLink}
                  onClick={() => setConfirmandoCancelamento(true)}
                >
                  <XCircle size={14} aria-hidden="true" />
                  Cancelar inscrição
                </button>
              )}

              {cancelamentoTravadoPorPrazo && (
                <p className={styles.motivo}>
                  Cancelamento encerrado (o prazo passou). Fale com a organização.
                </p>
              )}
            </div>
          ) : cena === 'pendente' ? (
            <div className={styles.pagamentoPendenteBloco}>
              <Link
                href={`/eventos/${eventoId}/pagamento/${minha?.cobrancaPendenteId}`}
                className={styles.pagamentoPendente}
              >
                <Clock size={16} aria-hidden="true" />
                <span>Pagamento pendente — continuar</span>
              </Link>
              <button
                type="button"
                className={styles.cancelarLink}
                onClick={() => setConfirmandoCancelamento(true)}
              >
                <XCircle size={14} aria-hidden="true" />
                Cancelar inscrição
              </button>
            </div>
          ) : cena === 'esgotado' ? (
            <button type="button" className={styles.botao} disabled>
              Vagas esgotadas
            </button>
          ) : (
            <>
              {avisoPrazoGestor}
              <button
                type="button"
                className={styles.botao}
                disabled={inscrever.isPending || navegandoParaCheckout || !!impedimento}
                onClick={() => tentarInscrever(inscreverDeVerdade)}
              >
                <CheckCircle2 size={18} aria-hidden="true" />
                {inscrever.isPending || navegandoParaCheckout ? 'Inscrevendo…' : 'Se inscrever'}
              </button>

              {impedimento && (
                <span className={styles.motivo}>
                  <AlertTriangle size={14} aria-hidden="true" />
                  {impedimento}
                </span>
              )}

              {semConta && preco && (
                <div className={styles.avisoSemConta}>
                  <AlertTriangle size={16} aria-hidden="true" />
                  <span>
                    Este evento é pago, mas a igreja ainda não conectou uma conta para receber
                    pagamentos.{' '}
                    {ehGestor ? (
                      <Link href="/configuracoes/igreja">Conectar agora</Link>
                    ) : (
                      'Fale com a secretaria da igreja.'
                    )}
                  </span>
                  <button type="button" className={styles.cancelarLink} onClick={() => setSemConta(false)}>
                    Fechar
                  </button>
                </div>
              )}
            </>
          )
        }
      />

      {confirmandoCancelamento && inscricaoId && (
        <ConfirmarCancelamentoInscricao
          nome=""
          proprio
          // Convidado agora é inscrição própria, sem vínculo ao cancelar o titular — a
          // contagem embutida não existe mais (ver Task 10/11) — sem substituto por ora.
          quantidadeConvidados={0}
          // Só o cartão "Inscrito" de evento pago encerrado por prazo perde o reembolso; o
          // "pagamento pendente" ainda não pagou nada.
          semReembolso={
            cenaAcao === 'inscrito'
            && preco != null
            && situacaoInscricao === 'ENCERRADA_POR_PRAZO'
            && politicaCancelamentoAposPrazo === 'PERMITIDO_SEM_REEMBOLSO'
          }
          isLoading={cancelar.isPending}
          onConfirmar={() =>
            cancelar.mutate(inscricaoId, { onSuccess: () => setConfirmandoCancelamento(false) })
          }
          onClose={() => setConfirmandoCancelamento(false)}
        />
      )}

      {modalContorno}
      {modalCompletarDados}
    </>
  )
}

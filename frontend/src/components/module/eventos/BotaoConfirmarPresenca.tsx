'use client'

import { useState } from 'react'
import { CheckCircle2, XCircle, ThumbsUp, AlertTriangle } from 'lucide-react'
import { useMinhaInscricao } from '@/hooks/inscricao/useMinhaInscricao'
import { useInscrever } from '@/hooks/inscricao/useInscrever'
import { useCancelarInscricao } from '@/hooks/inscricao/useCancelarInscricao'
import { useElegibilidade } from '@/hooks/inscricao/useElegibilidade'
import { ModalConfirmarPagamento } from './ModalConfirmarPagamento'
import { ConfirmarCancelamentoInscricao } from './ConfirmarCancelamentoInscricao'
import { ModalConfirmacao } from '@/components/common/ModalConfirmacao/ModalConfirmacao'
import { useAuthStore } from '@/store/authStore'
import { podeGerenciarInscricoes } from '@/lib/permissoes'
import { podeCancelarInscricao } from '@/lib/formats/eventoFormat'
import type { SituacaoEvento } from '@/types/evento.type'
import type { Impedimento } from '@/types/inscricao.type'
import styles from './BotaoConfirmarPresenca.module.css'

interface Props {
  eventoId: string
  inicioEm: string
  vagasRestantes: number | null
  requerInscricao: boolean
  situacao: SituacaoEvento
  preco?: number | null
  /** Chamado só quando a inscrição exige confirmação prévia (requerInscricao) e deu certo —
   *  o drawer usa isso pra abrir na hora o modal de campos personalizados, se o evento tiver. */
  onInscritoComSucesso?: () => void
}

export function BotaoConfirmarPresenca({
  eventoId, inicioEm, vagasRestantes, requerInscricao, situacao, preco, onInscritoComSucesso,
}: Props) {
  const [confirmandoCancelamento, setConfirmandoCancelamento] = useState(false)
  const [mostrarModalPagamento, setMostrarModalPagamento] = useState(false)
  // 422 contornável: gestor quebrando recorte de elegibilidade
  const [impedimentosParaConfirmar, setImpedimentosParaConfirmar] = useState<Impedimento[] | null>(null)

  const role = useAuthStore((s) => s.role)
  // Gestor ignora restrições com confirmação extra
  const ehGestor = podeGerenciarInscricoes(role)

  const { data: minha, isLoading } = useMinhaInscricao(eventoId)
  // Modo "Eu vou": sem toast, feedback é o próprio botão
  const inscrever = useInscrever(eventoId, !requerInscricao, {
    onContornavel: ehGestor ? (imps) => setImpedimentosParaConfirmar(imps) : undefined,
  })
  const cancelar = useCancelarInscricao(!requerInscricao)

  // Gestor vê o motivo, mas o botão segue ativo (422 abre confirmação)
  const { data: elegibilidade } = useElegibilidade(eventoId)
  const impedimentoPreview = !elegibilidade?.apto ? elegibilidade?.impedimentos[0]?.mensagem : undefined
  const impedimento = ehGestor ? undefined : impedimentoPreview

  const eventoEncerrado = new Date(inicioEm) < new Date()
  const semVagas = vagasRestantes !== null && vagasRestantes <= 0
  const inscricaoBloqueadaPelaSituacao = situacao !== 'AGENDADO'

  function aoConfirmarMesmoAssim() {
    inscrever.mutate({ confirmado: true }, {
      onSuccess: () => {
        setImpedimentosParaConfirmar(null)
        onInscritoComSucesso?.()
      },
    })
  }

  const modalContorno = impedimentosParaConfirmar && (
    <ModalConfirmacao
      titulo="Inscrever mesmo assim?"
      textoConfirmar="Inscrever mesmo assim"
      isLoading={inscrever.isPending}
      onConfirmar={aoConfirmarMesmoAssim}
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

    function aoClicar() {
      if (marcado) {
        if (!minha?.id) return
        cancelar.mutate(minha.id)
      } else {
        inscrever.mutate({})
      }
    }

    return (
      <span className={styles.euVouWrap}>
        <button
          type="button"
          className={`${styles.euVou} ${marcado ? styles.euVouAtivo : ''}`}
          onClick={aoClicar}
          disabled={pendente || bloqueadoPorImpedimento}
          aria-pressed={marcado}
        >
          <ThumbsUp size={15} className={styles.icone} aria-hidden="true" />
          {marcado ? 'Você vai' : 'Eu vou'}
        </button>
        {bloqueadoPorImpedimento && (
          <span className={styles.motivo}>
            <AlertTriangle size={13} aria-hidden="true" />
            {impedimento}
          </span>
        )}
        {modalContorno}
      </span>
    )
  }

  if (minha?.inscrito) {
    const podeCancelar = podeCancelarInscricao(situacao)

    return (
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

        {confirmandoCancelamento && (
          <ConfirmarCancelamentoInscricao
            nome=""
            proprio
            quantidadeConvidados={minha.acompanhantes.length}
            isLoading={cancelar.isPending}
            onConfirmar={() => {
              if (!minha.id) return
              cancelar.mutate(minha.id, {
                onSuccess: () => setConfirmandoCancelamento(false),
              })
            }}
            onClose={() => setConfirmandoCancelamento(false)}
          />
        )}
      </div>
    )
  }

  if (inscricaoBloqueadaPelaSituacao || eventoEncerrado) {
    return null
  }

  if (semVagas) {
    return (
      <button type="button" className={styles.botao} disabled>
        Vagas esgotadas
      </button>
    )
  }

  return (
    <>
      <button
        type="button"
        className={styles.botao}
        disabled={inscrever.isPending || !!impedimento}
        onClick={() => (preco
          ? setMostrarModalPagamento(true)
          : inscrever.mutate({}, { onSuccess: onInscritoComSucesso }))}
      >
        <CheckCircle2 size={18} aria-hidden="true" />
        {inscrever.isPending ? 'Confirmando…' : 'Confirmar presença'}
      </button>

      {impedimento && (
        <span className={styles.motivo}>
          <AlertTriangle size={14} aria-hidden="true" />
          {impedimento}
        </span>
      )}

      {mostrarModalPagamento && preco && (
        <ModalConfirmarPagamento
          preco={preco}
          isLoading={inscrever.isPending}
          onConfirmar={() => inscrever.mutate(undefined, {
            onSuccess: () => {
              setMostrarModalPagamento(false)
              onInscritoComSucesso?.()
            },
          })}
          onClose={() => setMostrarModalPagamento(false)}
        />
      )}

      {modalContorno}
    </>
  )
}

'use client'

import { useState } from 'react'
import { ModalConfirmacao } from '@/components/common/ModalConfirmacao/ModalConfirmacao'
import { ModalConfirmacaoCritica, type Consequencia } from '@/components/common/ModalConfirmacaoCritica/ModalConfirmacaoCritica'
import { ModalEscopoEdicaoEvento } from '@/components/module/eventos/ModalEscopoEdicaoEvento'
import { useArquivarEvento } from '@/hooks/evento/useArquivarEvento'
import { useListaInscritos } from '@/hooks/inscricao/useListaInscritos'
import { EventoResponse, type EscopoEdicaoEvento } from '@/types/evento.type'

/**
 * Confirmação por escrito só quando arquivar tem consequência real pra alguém: o evento já
 * tem gente inscrita (some da agenda/listagens dela) ou já aconteceu (histórico). Sem isso,
 * é atrito à toa — confirmação simples basta. Reversível nos dois casos (restaurar volta
 * tudo ao normal, inscrições e presença ficam intactas).
 */
export function ModalArquivarEvento({ evento, onClose }: { evento: EventoResponse; onClose: () => void }) {
  // Evento de série pergunta o alcance (só este/estes e os seguintes/toda a série) antes
  // de qualquer outra coisa — evento avulso (serieId null) pula direto pro fluxo de sempre.
  const [escopo, setEscopo] = useState<EscopoEdicaoEvento | undefined>(
    evento.serieId ? undefined : 'ESTA',
  )
  const { confirmar, isLoading, erroGeral } = useArquivarEvento(evento, onClose, escopo)
  const podePedirCount = evento.requerInscricao
  const { data: lista } = useListaInscritos(evento.id, podePedirCount, '', 0, 1)
  const totalInscritos = lista?.totalPessoas ?? 0
  const jaEncerrado = evento.situacao === 'ENCERRADO'

  if (escopo === undefined) {
    return (
      <ModalEscopoEdicaoEvento
        titulo={evento.titulo}
        pergunta="O que você quer arquivar?"
        onEscolher={setEscopo}
        onClose={onClose}
      />
    )
  }

  if (totalInscritos === 0 && !jaEncerrado) {
    return (
      <ModalConfirmacao
        titulo="Arquivar evento?"
        mensagem={<>Isso vai arquivar <strong>{evento.titulo}</strong>. Ele deixa de aparecer na agenda, mas pode ser restaurado depois.</>}
        textoConfirmar="Arquivar"
        perigo
        isLoading={isLoading}
        onConfirmar={confirmar}
        onClose={onClose}
      />
    )
  }

  const consequencias: Consequencia[] = []
  if (totalInscritos > 0) {
    consequencias.push({
      tipo: 'perde',
      texto: `Some da agenda e das listagens enquanto estiver arquivado — inclusive pra ${totalInscritos === 1 ? 'quem já se inscreveu' : `as ${totalInscritos} pessoas já inscritas`}`,
    })
    consequencias.push({ tipo: 'mantem', texto: 'As inscrições (e a presença já marcada) continuam vinculadas, só não aparecem enquanto arquivado' })
  } else {
    consequencias.push({ tipo: 'perde', texto: 'Some da agenda e de qualquer relatório/histórico que dependa do evento estar ativo, enquanto estiver arquivado' })
  }
  consequencias.push({ tipo: 'mantem', texto: 'É reversível: ao restaurar, volta a aparecer normalmente' })

  return (
    <ModalConfirmacaoCritica
      titulo="Arquivar evento?"
      mensagem={
        <>
          {totalInscritos > 0 ? (
            <>
              <strong>{evento.titulo}</strong> tem {totalInscritos === 1 ? '1 pessoa inscrita' : `${totalInscritos} pessoas inscritas`}.
            </>
          ) : (
            <>
              <strong>{evento.titulo}</strong> já aconteceu.
            </>
          )}
          {' '}Ao arquivar, ele some da agenda e das listagens enquanto estiver arquivado.
        </>
      }
      consequencias={consequencias}
      palavraConfirmacao={evento.titulo}
      textoConfirmar="Arquivar evento"
      isLoading={isLoading}
      erro={erroGeral}
      onConfirmar={confirmar}
      onClose={onClose}
    />
  )
}

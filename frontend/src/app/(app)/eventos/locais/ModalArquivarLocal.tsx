'use client'

import { ModalConfirmacaoCritica } from '@/components/common/ModalConfirmacaoCritica/ModalConfirmacaoCritica'
import { useArquivarLocalEvento } from '@/hooks/evento/useArquivarLocalEvento'
import type { LocalEventoResponse } from '@/types/evento.type'

/**
 * Confirmação "digite o nome" (não a leve): arquivar um local some com ele do
 * `<SeletorLocal>` do formulário de evento — quem tinha o hábito de escolhê-lo passa a
 * precisar digitar o nome como local ad-hoc. Vale o atrito extra de ler antes de confirmar.
 */
export function ModalArquivarLocal({ local, onClose }: { local: LocalEventoResponse; onClose: () => void }) {
  const { confirmar, isLoading, erroGeral } = useArquivarLocalEvento(local, onClose)

  return (
    <ModalConfirmacaoCritica
      titulo="Arquivar local?"
      mensagem={
        <>
          Ao arquivar <strong>{local.nome}</strong>, ele deixará de aparecer na lista de
          locais ao cadastrar um evento novo.
        </>
      }
      consequencias={[
        { tipo: 'perde', texto: 'Some da lista de locais do formulário de evento' },
        { tipo: 'mantem', texto: 'Eventos que já usam este local continuam mostrando o nome dele' },
      ]}
      palavraConfirmacao={local.nome}
      textoConfirmar="Arquivar local"
      isLoading={isLoading}
      erro={erroGeral}
      onConfirmar={confirmar}
      onClose={onClose}
    />
  )
}

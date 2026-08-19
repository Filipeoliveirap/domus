'use client'

import { ModalConfirmacao } from '@/components/common/ModalConfirmacao/ModalConfirmacao'
import { ModalConfirmacaoCritica } from '@/components/common/ModalConfirmacaoCritica/ModalConfirmacaoCritica'
import { useArquivarLocalEvento } from '@/hooks/evento/useArquivarLocalEvento'
import type { LocalEventoResponse } from '@/types/evento.type'

/**
 * Confirmação "digite o nome" só quando o local está em uso por algum evento — é aí que
 * arquivar tem consequência real: o evento fica SEM local (não vira texto livre com o nome
 * antigo — um local arquivado não é um endereço válido pra continuar aparecendo). Sem
 * evento vinculado, é atrito à toa — confirmação simples basta.
 */
export function ModalArquivarLocal({ local, onClose }: { local: LocalEventoResponse; onClose: () => void }) {
  const { confirmar, isLoading, erroGeral } = useArquivarLocalEvento(local, onClose)

  if (!local.temEvento) {
    return (
      <ModalConfirmacao
        titulo="Arquivar local?"
        mensagem={<>Isso vai arquivar <strong>{local.nome}</strong>. Ele deixa de aparecer na lista ao cadastrar um evento novo, mas pode ser restaurado depois.</>}
        textoConfirmar="Arquivar"
        perigo
        isLoading={isLoading}
        onConfirmar={confirmar}
        onClose={onClose}
      />
    )
  }

  return (
    <ModalConfirmacaoCritica
      titulo="Arquivar local?"
      mensagem={
        <>
          <strong>{local.nome}</strong> está em uso por pelo menos um evento. Ao arquivar,
          esse evento fica sem local nenhum.
        </>
      }
      consequencias={[
        {
          tipo: 'perde',
          texto: 'O evento que usa este local fica sem local: some o nome, a capacidade e o endereço — não vira texto livre',
        },
        { tipo: 'perde', texto: 'Some da lista de locais do formulário de evento' },
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

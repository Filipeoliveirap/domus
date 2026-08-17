'use client'

import { ModalConfirmacaoCritica } from '@/components/common/ModalConfirmacaoCritica/ModalConfirmacaoCritica'
import { useArquivarMinisterioConfirmacao } from '@/hooks/ministerio/useArquivarMinisterioConfirmacao'
import { ROTULO_MINISTERIO, ROTULO_MINISTERIO_PLURAL } from '@/lib/rotulosMinisterio'
import type { MinisterioResponse } from '@/types/ministerio.type'

/**
 * Confirmação "digite o nome" (não a leve): arquivar um ministério tira o acesso de todo
 * mundo que estava vinculado a ele — vale o atrito extra de ler antes de confirmar, mesmo
 * padrão de ModalArquivarLocal.tsx.
 */
export function ModalArquivarMinisterio({ ministerio, onClose }: { ministerio: MinisterioResponse; onClose: () => void }) {
  const { confirmar, isLoading, erroGeral } = useArquivarMinisterioConfirmacao(ministerio, onClose)
  const rotulo = ROTULO_MINISTERIO.toLowerCase()

  return (
    <ModalConfirmacaoCritica
      titulo={`Arquivar ${rotulo}?`}
      mensagem={
        <>
          Ao arquivar <strong>{ministerio.nome}</strong>, ela deixará de aparecer na lista de
          {' '}{ROTULO_MINISTERIO_PLURAL.toLowerCase()} e ninguém mais poderá ver ou pedir para entrar nela.
        </>
      }
      consequencias={[
        { tipo: 'perde', texto: `Some da lista de ${ROTULO_MINISTERIO_PLURAL.toLowerCase()} da igreja` },
        { tipo: 'mantem', texto: 'O histórico de quem foi membro não é apagado do banco' },
      ]}
      palavraConfirmacao={ministerio.nome}
      textoConfirmar={`Arquivar ${rotulo}`}
      isLoading={isLoading}
      erro={erroGeral}
      onConfirmar={confirmar}
      onClose={onClose}
    />
  )
}

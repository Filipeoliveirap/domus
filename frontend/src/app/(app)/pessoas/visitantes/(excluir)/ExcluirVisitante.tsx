'use client'

import { ModalConfirmacaoCritica } from '@/components/common/ModalConfirmacaoCritica/ModalConfirmacaoCritica'
import { useExcluirVisitante } from '@/hooks/visitante/useExcluirVisitante'
import type { VisitanteResponse } from '@/types/visitante.type'

export function ExcluirVisitante({ visitante, onClose }: { visitante: VisitanteResponse; onClose: () => void }) {
  const { confirmar, isLoading, erroGeral } = useExcluirVisitante(visitante, onClose)

  return (
    <ModalConfirmacaoCritica
      titulo="Excluir visitante?"
      mensagem={
        <>
          Ao excluir <strong>{visitante.nome}</strong>, todos os dados serão removidos
          permanentemente. Esta ação não pode ser desfeita.
        </>
      }
      consequencias={[
        { tipo: 'perde', texto: 'Os dados de contato e acompanhamento serão perdidos.' },
      ]}
      palavraConfirmacao={visitante.nome}
      textoConfirmar="Excluir visitante"
      isLoading={isLoading}
      erro={erroGeral}
      onConfirmar={confirmar}
      onClose={onClose}
    />
  )
}

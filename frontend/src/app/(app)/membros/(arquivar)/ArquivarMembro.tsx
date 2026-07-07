'use client'

import { ModalArquivar } from '@/components/common/modalArquivar/ModalArquivar'
import { useArquivarMembro } from '@/hooks/membro/useArquivarMembro'
import { MembroResponse } from '@/types/membro.type'

export function ModalArquivarMembro({ membro, onClose }: { membro: MembroResponse; onClose: () => void }) {
  const { confirmar, isLoading, erroGeral } = useArquivarMembro(membro, onClose)

  return (
    <ModalArquivar
      titulo="Arquivar membro?"
      mensagem={
        <>
          Ao arquivar <strong>{membro.nome}</strong>, ele deixará de aparecer na lista de membros.
          Seus dados e histórico serão preservados e poderão ser restaurados por um administrador a qualquer momento.
        </>
      }
      aviso="Se este membro tiver acesso ao sistema, o login também será arquivado e ele não poderá mais entrar."
      onConfirmar={confirmar}
      onClose={onClose}
      isLoading={isLoading}
      erro={erroGeral}
    />
  )
}
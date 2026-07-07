'use client'

import { ModalArquivar } from '@/components/common/modalArquivar/ModalArquivar'
import { useArquivarUsuario } from '@/hooks/usuario/useArquivarUsuario'
import { UsuarioResponse } from '@/types/usuario.types'

export function ModalArquivarUsuario({ usuario, onClose }: { usuario: UsuarioResponse; onClose: () => void }) {
  const { confirmar, isLoading, erroGeral } = useArquivarUsuario(usuario, onClose)

  return (
    <ModalArquivar
      titulo="Arquivar usuário?"
      mensagem={
        <>
          Ao arquivar <strong>{usuario.nome}</strong>, ele deixará de aparecer na lista ativa e perderá o
          acesso ao sistema. Seus dados e histórico serão preservados e movidos para a aba de
          &apos;Arquivados&apos;, onde poderão ser restaurados por um administrador a qualquer momento.
        </>
      }
      aviso="O arquivamento não exclui as contribuições financeiras ou registros históricos vinculados a este perfil."
      onConfirmar={confirmar}
      onClose={onClose}
      isLoading={isLoading}
      erro={erroGeral}
    />
  )
}
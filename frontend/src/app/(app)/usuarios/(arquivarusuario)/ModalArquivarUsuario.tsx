'use client'

import { ModalConfirmacaoCritica } from '@/components/common/ModalConfirmacaoCritica/ModalConfirmacaoCritica'
import { useArquivarUsuario } from '@/hooks/usuario/useArquivarUsuario'
import { UsuarioResponse } from '@/types/usuario.types'

/**
 * Confirmação por escrito: arquivar um usuário congela o nome dele (texto fixo) em todo
 * evento que criou/atualizou — isso NÃO desfaz mesmo restaurando o acesso depois. O acesso
 * em si é reversível; esse rastro de auditoria, não.
 */
export function ModalArquivarUsuario({ usuario, onClose }: { usuario: UsuarioResponse; onClose: () => void }) {
  const { confirmar, isLoading, erroGeral } = useArquivarUsuario(usuario, onClose)

  return (
    <ModalConfirmacaoCritica
      titulo="Arquivar usuário?"
      mensagem={
        <>
          Ao arquivar <strong>{usuario.nome}</strong>, ele perde o acesso ao sistema e sai da
          lista de usuários ativos.
        </>
      }
      consequencias={[
        {
          tipo: 'perde',
          texto: 'Os eventos que ele criou ou atualizou passam a mostrar o nome como texto fixo, em vez de link ativo — isso não volta mesmo restaurando o acesso depois',
        },
        { tipo: 'mantem', texto: 'O acesso ao sistema é revogado, mas pode ser restaurado a qualquer momento' },
        { tipo: 'mantem', texto: 'Histórico financeiro, célula, ministério e demais registros continuam intactos' },
      ]}
      palavraConfirmacao={usuario.nome}
      textoConfirmar="Arquivar usuário"
      isLoading={isLoading}
      erro={erroGeral}
      onConfirmar={confirmar}
      onClose={onClose}
    />
  )
}

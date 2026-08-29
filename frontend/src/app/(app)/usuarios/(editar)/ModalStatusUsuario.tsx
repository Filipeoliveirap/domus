'use client'
import { ModalConfirmacao } from '@/components/common/ModalConfirmacao/ModalConfirmacao'
import { useStatusUsuario } from '@/hooks/usuario/useStatusUsuario'
import { UsuarioResponse } from '@/types/usuario.types'

export function ModalStatusUsuario({ usuario, onClose }: { usuario: UsuarioResponse; onClose: () => void }) {
  const { confirmar, isLoading, erroGeral, novoStatus } = useStatusUsuario(usuario, onClose)
  const desativando = !novoStatus

  return (
    <ModalConfirmacao
      titulo={desativando ? 'Desativar acesso?' : 'Reativar acesso?'}
      perigo={desativando}
      textoConfirmar={desativando ? 'Confirmar desativação' : 'Confirmar reativação'}
      isLoading={isLoading}
      erro={erroGeral}
      onConfirmar={confirmar}
      onClose={onClose}
      mensagem={
        <>
          <p>
            Você está {desativando ? 'desativando' : 'reativando'} o acesso de{' '}
            <strong>{usuario.nome}</strong>.
          </p>
          <p>
            {desativando
              ? 'A pessoa perde o acesso na hora e não consegue mais fazer login até o acesso ser reativado.'
              : 'A pessoa volta a ter acesso e pode fazer login normalmente.'}
          </p>
          {desativando && (
            <p
              style={{
                borderLeft: '3px solid var(--color-primary)',
                background: 'var(--color-primary-light)',
                borderRadius: '4px',
                padding: '8px 12px',
                color: 'var(--color-text-secondary)',
              }}
            >
              <strong>É reversível.</strong> Nada é apagado — todos os dados e o histórico
              ficam preservados para auditoria. É diferente de excluir.
            </p>
          )}
        </>
      }
    />
  )
}

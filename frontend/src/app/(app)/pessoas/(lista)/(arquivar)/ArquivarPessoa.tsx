'use client'

import { ModalArquivar } from '@/components/common/modalArquivar/ModalArquivar'
import { useArquivarPessoa } from '@/hooks/pessoa/useArquivarPessoa'
import { PessoaResponse } from '@/types/pessoa.type'

export function ModalArquivarPessoa({ pessoa, onClose }: { pessoa: PessoaResponse; onClose: () => void }) {
  const { confirmar, isLoading, erroGeral } = useArquivarPessoa(pessoa, onClose)

  return (
    <ModalArquivar
      titulo="Arquivar pessoa?"
      mensagem={
        <>
          Ao arquivar <strong>{pessoa.nome}</strong>, ela deixará de aparecer na lista de pessoas.
          Seus dados e histórico serão preservados e poderão ser restaurados por um administrador a qualquer momento.
        </>
      }
      aviso="Se esta pessoa tiver acesso ao sistema, o login também será arquivado e ela não poderá mais entrar."
      onConfirmar={confirmar}
      onClose={onClose}
      isLoading={isLoading}
      erro={erroGeral}
    />
  )
}

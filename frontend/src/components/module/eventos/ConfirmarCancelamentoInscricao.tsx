'use client'

import { XCircle } from 'lucide-react'
import { ModalArquivar } from '@/components/common/modalArquivar/ModalArquivar'
import { ModalConfirmacaoCritica } from '@/components/common/ModalConfirmacaoCritica/ModalConfirmacaoCritica'

interface Props {
  nome: string
  proprio: boolean
  quantidadeConvidados: number
  isLoading: boolean
  erro?: string | null
  onConfirmar: () => void
  onClose: () => void
}

export function ConfirmarCancelamentoInscricao({
  nome, proprio, quantidadeConvidados, isLoading, erro, onConfirmar, onClose,
}: Props) {
  const temConvidados = quantidadeConvidados > 0

  if (temConvidados) {
    const textoConvidados = quantidadeConvidados === 1
      ? `${proprio ? 'Seu convidado' : 'O convidado dessa pessoa'} será removido e não volta sozinho numa nova inscrição.`
      : `${proprio ? 'Seus' : 'Os'} ${quantidadeConvidados} convidados ${proprio ? '' : 'dessa pessoa '}serão removidos e não voltam sozinhos numa nova inscrição.`

    return (
      <ModalConfirmacaoCritica
        titulo="Cancelar inscrição"
        mensagem={
          proprio
            ? 'Você está prestes a cancelar sua inscrição neste evento.'
            : <>Você está prestes a cancelar a inscrição de <strong>{nome}</strong>. Esta ação não pode ser desfeita pela pessoa.</>
        }
        consequencias={[{ tipo: 'perde', texto: textoConvidados }]}
        palavraConfirmacao={proprio ? 'CANCELAR' : nome}
        textoConfirmar="Cancelar inscrição"
        isLoading={isLoading}
        erro={erro}
        onConfirmar={onConfirmar}
        onClose={onClose}
      />
    )
  }

  return (
    <ModalArquivar
      titulo="Cancelar inscrição"
      icone={XCircle}
      reversivel={false}
      mensagem={
        proprio
          ? 'Tem certeza que deseja cancelar sua inscrição neste evento?'
          : <>Tem certeza que deseja cancelar a inscrição de <strong>{nome}</strong>?</>
      }
      textoConfirmar="Cancelar inscrição"
      textoCarregando="Cancelando…"
      isLoading={isLoading}
      erro={erro}
      onConfirmar={onConfirmar}
      onClose={onClose}
    />
  )
}

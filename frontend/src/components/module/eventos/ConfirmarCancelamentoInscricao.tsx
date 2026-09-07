'use client'

import { XCircle } from 'lucide-react'
import { ModalArquivar } from '@/components/common/modalArquivar/ModalArquivar'
import {
  ModalConfirmacaoCritica,
  type Consequencia,
} from '@/components/common/ModalConfirmacaoCritica/ModalConfirmacaoCritica'

interface Props {
  nome: string
  proprio: boolean
  quantidadeConvidados: number
  isLoading: boolean
  erro?: string | null
  /** Evento pago cancelado após o prazo de inscrição: não há estorno do valor pago. */
  semReembolso?: boolean
  onConfirmar: () => void
  onClose: () => void
}

export function ConfirmarCancelamentoInscricao({
  nome, proprio, quantidadeConvidados, isLoading, erro, semReembolso = false, onConfirmar, onClose,
}: Props) {
  const temConvidados = quantidadeConvidados > 0

  if (temConvidados || semReembolso) {
    const consequencias: Consequencia[] = []

    if (temConvidados) {
      const textoConvidados = quantidadeConvidados === 1
        ? `${proprio ? 'Seu convidado' : 'O convidado dessa pessoa'} será removido e não volta sozinho numa nova inscrição.`
        : `${proprio ? 'Seus' : 'Os'} ${quantidadeConvidados} convidados ${proprio ? '' : 'dessa pessoa '}serão removidos e não voltam sozinhos numa nova inscrição.`
      consequencias.push({ tipo: 'perde', texto: textoConvidados })
    }

    if (semReembolso) {
      consequencias.push({
        tipo: 'perde',
        texto: proprio
          ? 'O prazo de inscrição já passou — se cancelar agora, o valor pago NÃO será reembolsado.'
          : 'O prazo de inscrição já passou — ao remover, o valor pago por esta pessoa NÃO será reembolsado.',
      })
    }

    return (
      <ModalConfirmacaoCritica
        titulo="Cancelar inscrição"
        mensagem={
          proprio
            ? 'Você está prestes a cancelar sua inscrição neste evento.'
            : <>Você está prestes a cancelar a inscrição de <strong>{nome}</strong>. Esta ação não pode ser desfeita pela pessoa.</>
        }
        consequencias={consequencias}
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

'use client'

import { XCircle } from 'lucide-react'
import { ModalArquivar } from '@/components/common/modalArquivar/ModalArquivar'
import { ModalConfirmacaoCritica } from '@/components/common/ModalConfirmacaoCritica/ModalConfirmacaoCritica'

interface Props {
  /** Nome de quem está sendo cancelado — usado na palavra de confirmação e na mensagem. */
  nome: string
  /** `true` = a própria pessoa está cancelando; `false` = ADMIN/LÍDER cancelando outra. */
  proprio: boolean
  quantidadeConvidados: number
  isLoading: boolean
  erro?: string | null
  onConfirmar: () => void
  onClose: () => void
}

/**
 * A regra de atrito do cancelamento de inscrição, num só lugar.
 *
 * <p>O que decide entre confirmação leve e confirmação pesada (digitar o nome) <b>não é
 * quem cancela</b> — é se a ação arrasta <b>convidados</b> junto. Cancelar deleta os
 * convidados da inscrição (eles não voltam sozinhos numa nova inscrição), então quando há
 * convidado o estrago é maior que "só essa pessoa sai da lista" e o atrito sobe para
 * digitar o nome. Sem convidado, é uma confirmação simples.
 *
 * <p>Usado nos três lugares onde alguém cancela uma inscrição: a lista de inscritos
 * (ADMIN/LÍDER), o modal "Quem vai" (ADMIN/LÍDER) e o cartão de evento em `/inicio`
 * (a própria pessoa).
 */
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

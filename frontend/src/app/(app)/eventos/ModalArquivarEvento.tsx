'use client'

import { ModalArquivar } from '@/components/common/modalArquivar/ModalArquivar'
import { useArquivarEvento } from '@/hooks/evento/useArquivarEvento'
import { EventoResponse } from '@/types/evento.type'

export function ModalArquivarEvento({ evento, onClose }: { evento: EventoResponse; onClose: () => void }) {
  const { confirmar, isLoading, erroGeral } = useArquivarEvento(evento, onClose)

  return (
    <ModalArquivar
      titulo="Arquivar evento?"
      mensagem={
        <>
          Ao arquivar <strong>{evento.titulo}</strong>, ele deixará de aparecer na agenda da igreja.
          Os dados serão preservados e poderão ser restaurados por um administrador a qualquer momento.
        </>
      }
      aviso="O arquivamento não remove registros históricos vinculados a este evento."
      onConfirmar={confirmar}
      onClose={onClose}
      isLoading={isLoading}
      erro={erroGeral}
    />
  )
}
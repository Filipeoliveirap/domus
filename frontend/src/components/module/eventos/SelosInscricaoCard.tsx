'use client'

import { AlertTriangle, CheckCircle2 } from 'lucide-react'
import { useMinhaInscricao } from '@/hooks/inscricao/useMinhaInscricao'
import { useCamposPersonalizados } from '@/hooks/evento/useCamposPersonalizados'
import { useRespostasCampos } from '@/hooks/inscricao/useRespostasCampos'
import type { EventoResponse } from '@/types/evento.type'
import styles from './SelosInscricaoCard.module.css'

/** Dois selos no card do evento, só quando fazem sentido: "Você está inscrito" (ajuda a
 *  achar entre muitos cards qual já tem inscrição) e um aviso clicável de pendência, quando
 *  falta responder algum campo obrigatório — clicar abre o evento já no modal de resposta. */
export function SelosInscricaoCard({
  evento, onAbrirPendencia,
}: { evento: EventoResponse; onAbrirPendencia: () => void }) {
  const { data: minha } = useMinhaInscricao(evento.requerInscricao ? evento.id : undefined)
  const { data: campos } = useCamposPersonalizados(evento.requerInscricao ? evento.id : '')
  const inscricaoId = minha?.inscrito ? minha.id ?? undefined : undefined
  const { data: respostas } = useRespostasCampos(inscricaoId ?? '')

  if (!minha?.inscrito) return null

  const obrigatorios = (campos ?? []).filter((c) => c.obrigatorio)
  const respondidos = new Set((respostas ?? []).filter((r) => r.valor?.trim()).map((r) => r.campoId))
  const pendentes = obrigatorios.filter((c) => !respondidos.has(c.id))

  return (
    <span className={styles.wrap} onClick={(e) => e.stopPropagation()}>
      <span className={styles.seloInscrito}>
        <CheckCircle2 size={12} aria-hidden="true" />
        Você está inscrito
      </span>
      {pendentes.length > 0 && (
        <button
          type="button"
          className={styles.seloPendencia}
          title={`Falta responder: ${pendentes.map((c) => c.label).join(', ')}`}
          onClick={onAbrirPendencia}
        >
          <AlertTriangle size={12} aria-hidden="true" />
          {pendentes.length === 1 ? '1 pendência' : `${pendentes.length} pendências`}
        </button>
      )}
    </span>
  )
}

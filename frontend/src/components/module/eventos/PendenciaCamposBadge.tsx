'use client'

import { AlertTriangle } from 'lucide-react'
import { useRespostasCampos } from '@/hooks/inscricao/useRespostasCampos'
import type { CampoPersonalizadoResponse } from '@/types/campoPersonalizado.type'
import styles from './PendenciaCamposBadge.module.css'

/** Só renderiza algo quando falta responder pelo menos um campo obrigatório do evento —
 *  fica ao lado do nome na lista de inscritos, pra quem gerencia bater o olho e ver quem
 *  falta cobrar, sem abrir um por um. */
export function PendenciaCamposBadge({
  inscricaoId, camposObrigatorios,
}: { inscricaoId: string; camposObrigatorios: CampoPersonalizadoResponse[] }) {
  const { data: respostas } = useRespostasCampos(inscricaoId)

  if (camposObrigatorios.length === 0 || !respostas) return null

  const respondidos = new Set(
    respostas.filter((r) => r.valor?.trim()).map((r) => r.campoId),
  )
  const pendentes = camposObrigatorios.filter((c) => !respondidos.has(c.id))

  if (pendentes.length === 0) return null

  return (
    <span className={styles.badge} title={`Falta responder: ${pendentes.map((c) => c.label).join(', ')}`}>
      <AlertTriangle size={12} aria-hidden="true" />
      {pendentes.length === 1 ? '1 pendência' : `${pendentes.length} pendências`}
    </span>
  )
}

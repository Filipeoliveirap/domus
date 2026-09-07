'use client'

import { CalendarClock } from 'lucide-react'
import type { EventoResponse } from '@/types/evento.type'
import styles from './SeloPrazoInscricao.module.css'

const DIAS_DESTAQUE = 3

function formatarData(iso: string): string {
  return new Date(iso).toLocaleDateString('pt-BR', { day: '2-digit', month: '2-digit' })
}

function diasAte(iso: string): number {
  return Math.ceil((new Date(iso).getTime() - Date.now()) / 86_400_000)
}

export function SeloPrazoInscricao({ evento }: { evento: EventoResponse }) {
  if (!evento.inscricoesAte || evento.situacaoInscricao === 'ENCERRADA_POR_INICIO') return null

  const data = formatarData(evento.inscricoesAte)

  if (evento.situacaoInscricao === 'ENCERRADA_POR_PRAZO') {
    return (
      <span className={styles.encerrado} onClick={(e) => e.stopPropagation()}>
        <CalendarClock size={12} aria-hidden="true" /> Inscrições encerradas
      </span>
    )
  }

  const diasRestantes = diasAte(evento.inscricoesAte)
  const destaque = diasRestantes >= 0 && diasRestantes <= DIAS_DESTAQUE
  return (
    <span className={destaque ? styles.destaque : styles.neutro} onClick={(e) => e.stopPropagation()}>
      <CalendarClock size={12} aria-hidden="true" />
      Inscrições até {data}
      {destaque && ` · faltam ${diasRestantes} dia${diasRestantes === 1 ? '' : 's'}`}
    </span>
  )
}

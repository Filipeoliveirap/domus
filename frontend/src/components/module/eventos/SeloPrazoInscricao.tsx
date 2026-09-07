'use client'

import { CalendarClock } from 'lucide-react'
import type { EventoResponse } from '@/types/evento.type'
import styles from './SeloPrazoInscricao.module.css'

const DIAS_DESTAQUE = 3

function formatarData(iso: string): string {
  return new Date(iso).toLocaleDateString('pt-BR', { day: '2-digit', month: '2-digit' })
}

function diasAte(iso: string): number {
  const hoje = new Date()
  hoje.setHours(0, 0, 0, 0)
  const alvo = new Date(iso)
  alvo.setHours(0, 0, 0, 0)
  return Math.round((alvo.getTime() - hoje.getTime()) / 86_400_000)
}

function horaDe(iso: string): string {
  return new Date(iso).toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' })
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

  let sufixo = ''
  if (diasRestantes === 0) {
    sufixo = ` · encerra hoje às ${horaDe(evento.inscricoesAte)}`
  } else if (diasRestantes === 1) {
    sufixo = ` · encerra amanhã`
  } else if (diasRestantes > 1 && diasRestantes <= DIAS_DESTAQUE) {
    sufixo = ` · faltam ${diasRestantes} dias`
  }

  return (
    <span className={destaque ? styles.destaque : styles.neutro} onClick={(e) => e.stopPropagation()}>
      <CalendarClock size={12} aria-hidden="true" />
      Inscrições até {data}
      {sufixo}
    </span>
  )
}

import type { EventoResponse } from '@/types/evento.type'

const MESES = ['JAN', 'FEV', 'MAR', 'ABR', 'MAI', 'JUN',
               'JUL', 'AGO', 'SET', 'OUT', 'NOV', 'DEZ']


export function dataAgenda(iso: string): { dia: string; mes: string; ano: string | null } {
  const d = new Date(iso)
  const anoAtual = new Date().getFullYear()
  const ano = d.getFullYear()
  return {
    dia: String(d.getDate()).padStart(2, '0'),
    mes: MESES[d.getMonth()],
    ano: ano !== anoAtual ? String(ano) : null,   
  }
}

export function hora(iso: string): string {
  const d = new Date(iso)
  return d.toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' })
}

export function dataExtenso(iso: string): string {
  const d = new Date(iso)
  const txt = d.toLocaleDateString('pt-BR', {
    weekday: 'long', day: '2-digit', month: 'long', year: 'numeric',
  })
  return txt.charAt(0).toUpperCase() + txt.slice(1)
}

export type StatusEvento = 'EM_BREVE' | 'HOJE' | 'ENCERRADO'

export function statusEvento(evento: EventoResponse): StatusEvento {
  const agora = new Date()
  const inicio = new Date(evento.inicioEm)
  const fim = evento.fimEm ? new Date(evento.fimEm) : inicio

  if (fim < agora) return 'ENCERRADO'

  const mesmoDia = inicio.toDateString() === agora.toDateString()
  if (mesmoDia) return 'HOJE'

  return 'EM_BREVE'
}

export function rotuloStatus(status: StatusEvento): string {
  const mapa: Record<StatusEvento, string> = {
    EM_BREVE: 'Em breve',
    HOJE: 'Hoje',
    ENCERRADO: 'Encerrado',
  }
  return mapa[status]
}

export function varianteStatus(status: StatusEvento): string {
  const mapa: Record<StatusEvento, string> = {
    EM_BREVE: 'statusEmBreve',
    HOJE: 'statusHoje',
    ENCERRADO: 'statusEncerrado',
  }
  return mapa[status]
}
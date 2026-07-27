import type { EventoResponse, SituacaoEvento } from '@/types/evento.type'
import type { ParticipanteResponse } from '@/types/inscricao.type'

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

export function periodoEvento(evento: Pick<EventoResponse, 'inicioEm' | 'fimEm'>): string {
  if (!evento.fimEm) return dataExtenso(evento.inicioEm)

  const mesmoDia = new Date(evento.inicioEm).toDateString() === new Date(evento.fimEm).toDateString()
  if (mesmoDia) return dataExtenso(evento.inicioEm)

  return `${dataExtenso(evento.inicioEm)} — ${dataExtenso(evento.fimEm)}`
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

export function rotuloSituacao(situacao: SituacaoEvento): string {
  const mapa: Record<SituacaoEvento, string> = {
    AGENDADO: 'Agendado',
    EM_ANDAMENTO: 'Em andamento',
    ENCERRADO: 'Encerrado',
  }
  return mapa[situacao]
}

export function varianteSituacao(situacao: SituacaoEvento): string {
  const mapa: Record<SituacaoEvento, string> = {
    AGENDADO: 'statusEmBreve',
    EM_ANDAMENTO: 'statusHoje',
    ENCERRADO: 'statusEncerrado',
  }
  return mapa[situacao]
}

export function seloEvento(evento: EventoResponse): { label: string; variante: string } {
  if (evento.situacao !== 'AGENDADO') {
    return { label: rotuloSituacao(evento.situacao), variante: varianteSituacao(evento.situacao) }
  }
  const status = statusEvento(evento)
  return { label: rotuloStatus(status), variante: varianteStatus(status) }
}

export function podeEditarEvento(situacao: SituacaoEvento): boolean {
  return situacao === 'AGENDADO'
}

export function podeArquivarEvento(situacao: SituacaoEvento): boolean {
  return situacao !== 'EM_ANDAMENTO'
}

export function podeCancelarInscricao(situacao: SituacaoEvento): boolean {
  return situacao === 'AGENDADO'
}

export function vagasRestantesCalc(
  vagas: number | null,
  participantes: ParticipanteResponse[],
): number | null {
  if (vagas == null) return null
  const ocupadas = participantes.reduce((acc, p) => acc + 1 + p.convidados.length, 0)
  return Math.max(0, vagas - ocupadas)
}

export function vagasAcabando(vagas: number | null, vagasRestantes: number | null): boolean {
  if (vagas == null || vagasRestantes == null) return false
  return vagasRestantes > 0 && (vagasRestantes <= 5 || vagasRestantes / vagas <= 0.2)
}

export function vagasEsgotadas(vagas: number | null, vagasRestantes: number | null): boolean {
  return vagas != null && vagasRestantes === 0
}

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

/** Rótulo da situação real do evento (backend), usada para o selo e para travar edição/arquivamento. */
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

/** Evento não pode ser editado (nem no front, nem no back) fora de AGENDADO. */
export function podeEditarEvento(situacao: SituacaoEvento): boolean {
  return situacao === 'AGENDADO'
}

/** Espelha a regra do backend: arquivar é proibido só em EM_ANDAMENTO (ENCERRADO pode). */
export function podeArquivarEvento(situacao: SituacaoEvento): boolean {
  return situacao !== 'EM_ANDAMENTO'
}

/**
 * A3/rodada 3: o backend recusa cancelar inscrição (própria ou de outra pessoa) fora de
 * AGENDADO, sem exceção para ADMIN/LÍDER — presença já registrada é histórico, não algo
 * que se desfaz depois que o evento começou ou terminou.
 */
export function podeCancelarInscricao(situacao: SituacaoEvento): boolean {
  return situacao === 'AGENDADO'
}

/**
 * Vagas restantes calculadas a partir da lista de participantes (cada um ocupa 1 vaga +
 * 1 por convidado). `null` = evento sem limite de vagas.
 */
export function vagasRestantesCalc(
  vagas: number | null,
  participantes: ParticipanteResponse[],
): number | null {
  if (vagas == null) return null
  const ocupadas = participantes.reduce((acc, p) => acc + 1 + p.convidados.length, 0)
  return Math.max(0, vagas - ocupadas)
}

/**
 * F4/rodada 2 (F7): aviso de vagas acabando — limiar de 20% ou menos, ou 5 ou menos
 * restantes. Esgotado (0) tem aviso próprio (`vagasEsgotadas`) — não é "últimas vagas".
 */
export function vagasAcabando(vagas: number | null, vagasRestantes: number | null): boolean {
  if (vagas == null || vagasRestantes == null) return false
  return vagasRestantes > 0 && (vagasRestantes <= 5 || vagasRestantes / vagas <= 0.2)
}

/** F7: esgotado — "últimas 0 vagas" era absurdo; isto é o estado real de vaga zerada. */
export function vagasEsgotadas(vagas: number | null, vagasRestantes: number | null): boolean {
  return vagas != null && vagasRestantes === 0
}
import type { DiaSemana } from '@/types/celula.type'

export function rotuloDiaSemana(dia: DiaSemana | null): string {
  if (!dia) return '—'
  const mapa: Record<DiaSemana, string> = {
    SEGUNDA: 'Segundas',
    TERCA: 'Terças',
    QUARTA: 'Quartas',
    QUINTA: 'Quintas',
    SEXTA: 'Sextas',
    SABADO: 'Sábados',
    DOMINGO: 'Domingos',
  }
  return mapa[dia]
}

export function formatarHorario(horario: string | null): string {
  if (!horario) return ''
  return horario.slice(0, 5)
}

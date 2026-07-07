import type { StatusMembro } from '@/types/membro.type'

export function iniciais(nome: string): string {
  const partes = nome.trim().split(/\s+/)
  if (partes.length === 1) return partes[0].slice(0, 2).toUpperCase()
  return (partes[0][0] + partes[partes.length - 1][0]).toUpperCase()
}

export function rotuloStatus(status: StatusMembro): string {
  const mapa: Record<StatusMembro, string> = {
    ATIVO: 'Ativo',
    INATIVO: 'Inativo',
    VISITANTE: 'Visitante',
  }
  return mapa[status] ?? status
}

export function varianteStatus(status: StatusMembro): string {
  const mapa: Record<StatusMembro, string> = {
    ATIVO: 'statusAtivo',
    INATIVO: 'statusInativo',
    VISITANTE: 'statusVisitante',
  }
  return mapa[status] ?? 'statusInativo'
}

export function formatarData(iso: string): string {
  const data = new Date(iso)
  return data.toLocaleDateString('pt-BR', {
    day: '2-digit',
    month: 'short',
    year: 'numeric',
  }).replace('.', '') 
}

export function formatarTelefoneExibicao(telefone: string | null): string {
  if (!telefone) return '—'
  const d = telefone.replace(/\D/g, '')
  if (d.length === 11) return `(${d.slice(0, 2)}) ${d.slice(2, 7)}-${d.slice(7)}`
  if (d.length === 10) return `(${d.slice(0, 2)}) ${d.slice(2, 6)}-${d.slice(6)}`
  return telefone
}
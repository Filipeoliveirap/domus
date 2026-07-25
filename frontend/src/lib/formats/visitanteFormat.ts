import type { VisitanteResponse } from '@/types/visitante.type'

export function iniciaisVisitante(nome: string): string {
  const partes = nome.trim().split(/\s+/)
  if (partes.length === 1) return partes[0].slice(0, 2).toUpperCase()
  return (partes[0][0] + partes[partes.length - 1][0]).toUpperCase()
}

export function formatarTelefoneExibicao(telefone: string | null): string {
  if (!telefone) return '—'
  const d = telefone.replace(/\D/g, '')
  if (d.length === 11) return `(${d.slice(0, 2)}) ${d.slice(2, 7)}-${d.slice(7)}`
  if (d.length === 10) return `(${d.slice(0, 2)}) ${d.slice(2, 6)}-${d.slice(6)}`
  return telefone
}

export function formatarData(iso: string): string {
  const data = new Date(iso)
  return data.toLocaleDateString('pt-BR', {
    day: '2-digit',
    month: 'short',
    year: 'numeric',
  }).replace('.', '')
}

export function formatarDataNascimento(iso: string | null): string | null {
  if (!iso) return null
  const [ano, mes, dia] = iso.split('-').map(Number)
  if (!ano || !mes || !dia) return null
  return new Date(ano, mes - 1, dia).toLocaleDateString('pt-BR', {
    day: '2-digit',
    month: 'short',
    year: 'numeric',
  }).replace('.', '')
}

export function calcularIdade(iso: string | null): number | null {
  if (!iso) return null
  const [ano, mes, dia] = iso.split('-').map(Number)
  if (!ano || !mes || !dia) return null

  const hoje = new Date()
  const nascimento = new Date(ano, mes - 1, dia)
  let idade = hoje.getFullYear() - nascimento.getFullYear()

  const mesAtual = hoje.getMonth()
  const diaAtual = hoje.getDate()
  if (mesAtual < mes - 1 || (mesAtual === mes - 1 && diaAtual < dia)) {
    idade--
  }

  return idade
}

export function rotuloEstadoCivil(estadoCivil: string | null): string {
  if (!estadoCivil) return ''
  const mapa: Record<string, string> = {
    SOLTEIRO: 'Solteiro(a)',
    CASADO: 'Casado(a)',
    DIVORCIADO: 'Divorciado(a)',
    VIUVO: 'Viúvo(a)',
  }
  return mapa[estadoCivil] ?? estadoCivil
}

export function formatarEndereco(e: VisitanteResponse['endereco']): { linha1: string; linha2: string } | null {
  if (!e) return null
  const ruaNumero = [e.logradouro, e.numero].filter(Boolean).join(', ')
  const linha1 = [ruaNumero, e.complemento].filter(Boolean).join(' — ')
  const cidadeUf = [e.cidade, e.uf].filter(Boolean).join('/')
  const cepFmt = e.cep ? e.cep.replace(/^(\d{5})(\d{3})$/, '$1-$2') : ''
  const linha2 = [e.bairro, cidadeUf, cepFmt && `CEP ${cepFmt}`].filter(Boolean).join(' · ')
  if (!linha1 && !linha2) return null
  return { linha1, linha2 }
}

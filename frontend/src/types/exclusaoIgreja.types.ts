export interface ResumoExclusao {
  pessoas: number
  eventos: number
  movimentacoesFinanceiras: number
  celulas: number
  ministerios: number
  usuarios: number
  igrejasVinculadas: string[]
}

export interface AgendarExclusaoPayload {
  nomeConfirmacao: string
  senha?: string
  googleIdToken?: string
}

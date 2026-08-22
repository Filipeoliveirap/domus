export type TipoCampoPersonalizado = 'TEXTO_CURTO' | 'OPCAO_UNICA' | 'MULTIPLA_ESCOLHA' | 'SIM_NAO'

export type MapeamentoCampoPersonalizado = 'IDADE' | 'ESTADO_CIVIL' | 'SEXO' | 'ENDERECO'

export interface CampoPersonalizadoResponse {
  id: string
  label: string
  placeholder: string | null
  tipo: TipoCampoPersonalizado
  opcoes: string[]
  obrigatorio: boolean
  visivelAoPublico: boolean
  ordem: number
  mapeamento: MapeamentoCampoPersonalizado | null
}

export interface CampoPersonalizadoRequest {
  id: string | null
  label: string
  placeholder: string | null
  tipo: TipoCampoPersonalizado
  opcoes: string[] | null
  obrigatorio: boolean
  visivelAoPublico: boolean
  ordem: number
  mapeamento: MapeamentoCampoPersonalizado | null
}

export interface RespostaRequest {
  campoId: string
  valor: string
}

export interface RespostaResponse {
  campoId: string
  label: string
  tipo: TipoCampoPersonalizado
  valor: string
}

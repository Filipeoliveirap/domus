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

/** RESPONDIDO — a pessoa respondeu de fato (valor preenchido).
 *  CADASTRO — campo mapeado (idade/estado civil/sexo/endereço) preenchido a partir do
 *  cadastro da pessoa na igreja; ela nunca respondeu no evento, mas o dado existe.
 *  SEM_RESPOSTA — sem resposta e sem dado no cadastro (valor é null). */
export type OrigemResposta = 'RESPONDIDO' | 'CADASTRO' | 'SEM_RESPOSTA'

export interface RespostaResponse {
  campoId: string
  label: string
  tipo: TipoCampoPersonalizado
  valor: string | null
  origem: OrigemResposta
}

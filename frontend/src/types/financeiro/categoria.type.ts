export type TipoCategoria = 'ENTRADA' | 'SAIDA' | 'AMBOS'

export interface CategoriaResponse {
  id: string
  nome: string
  tipo: TipoCategoria
}

export interface CategoriaRequest {
  nome: string
  tipo: TipoCategoria
}
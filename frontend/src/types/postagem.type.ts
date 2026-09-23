export type TipoPostagem = 'MURAL_AVISO' | 'DEVOCIONAL' | 'PEDIDO_ORACAO' | 'TESTEMUNHO' | 'RESUMO_CULTO' | 'GERAL'
export type TipoReacao = 'AMEM' | 'CORACAO' | 'ORANDO'

export interface AutorPostagem {
  id: string
  nome: string
  fotoId: string | null
  cargo?: string | null
}

export interface Comentario {
  id: string
  autor: AutorPostagem
  conteudo: string
  criadoEm: string
}

export interface Postagem {
  id: string
  autor: AutorPostagem
  tipo: TipoPostagem
  oficial: boolean
  titulo?: string | null
  conteudo: string
  fotoId?: string | null
  versiculoRef?: string | null
  fixado: boolean
  criadoEm: string
  totalCurtidas: number
  totalComentarios: number
  minhaReacao: TipoReacao | null
  comentariosRecentes?: Comentario[]
}

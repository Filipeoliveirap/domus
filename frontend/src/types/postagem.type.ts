export type TipoPostagem = 'MURAL_AVISO' | 'DEVOCIONAL' | 'PEDIDO_ORACAO' | 'TESTEMUNHO' | 'RESUMO_CULTO' | 'GERAL'
export type TipoReacao = 'AMEM' | 'CORACAO' | 'ORANDO'

export interface AutorPostagem {
  id: string
  nome: string
  fotoId: string | null
  cargo?: string | null
}

export interface IgrejaResumo {
  id: string
  nome: string
  sigla: string | null
}

export interface Comentario {
  id: string
  autor: AutorPostagem
  igrejaAutor?: IgrejaResumo | null
  conteudo: string
  paiComentarioId?: string | null
  totalCurtidas: number
  curtidoPorMim: boolean
  podeDeletar?: boolean
  criadoEm: string
}

export interface Postagem {
  id: string
  autor: AutorPostagem
  igrejaAutor?: IgrejaResumo | null
  tipo: TipoPostagem
  oficial: boolean
  titulo?: string | null
  conteudo: string
  fotoId?: string | null
  versiculoRef?: string | null
  fixado: boolean
  restritoPropriaIgreja?: boolean
  podeEditar?: boolean
  podeDeletar?: boolean
  criadoEm: string
  totalCurtidas: number
  totalComentarios: number
  minhaReacao: TipoReacao | null
  comentariosRecentes?: Comentario[]
}

export interface CriarPostagemData {
  tipo: TipoPostagem
  oficial: boolean
  titulo?: string
  conteudo?: string
  fotoId?: string
  versiculoRef?: string
  fixado?: boolean
  restritoPropriaIgreja?: boolean
}

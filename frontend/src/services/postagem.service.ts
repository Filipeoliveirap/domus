import { api } from '@/lib/api'
import { Endpoints } from '@/lib/endpoints'
import type { Postagem, Comentario, TipoPostagem, TipoReacao } from '@/types/postagem.type'

export interface CriarPostagemPayload {
  tipo: TipoPostagem
  oficial: boolean
  titulo?: string | null
  conteudo: string
  fotoId?: string | null
  versiculoRef?: string | null
  fixado?: boolean
}

export interface PaginaFeedResponse {
  content: Postagem[]
  totalPages: number
  totalElements: number
  number: number
}

export const postagemService = {
  listarMural: (): Promise<Postagem[]> =>
    api.get<Postagem[]>(Endpoints.postagens.MURAL).then((res) => res.data),

  listarFeed: (tipo?: TipoPostagem, page = 0, size = 10): Promise<PaginaFeedResponse> =>
    api
      .get<PaginaFeedResponse>(Endpoints.postagens.FEED, {
        params: { tipo: tipo || undefined, page, size },
      })
      .then((res) => res.data),

  criar: (payload: CriarPostagemPayload): Promise<Postagem> =>
    api.post<Postagem>(Endpoints.postagens.CRIAR, payload).then((res) => res.data),

  curtir: (postagemId: string, tipo: TipoReacao): Promise<Postagem> =>
    api.post<Postagem>(Endpoints.postagens.CURTIR(postagemId), { tipo }).then((res) => res.data),

  comentar: (postagemId: string, conteudo: string): Promise<Comentario> =>
    api
      .post<Comentario>(Endpoints.postagens.COMENTAR(postagemId), { conteudo })
      .then((res) => res.data),

  deletar: (postagemId: string): Promise<void> =>
    api.delete(Endpoints.postagens.DELETAR(postagemId)).then(() => undefined),
}

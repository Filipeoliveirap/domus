import { api } from '@/lib/api'
import { Endpoints } from '@/lib/endpoints'
import type {
  MembroRequest,
  MembroResponse,
  ConcederAcessoRequest,
} from '@/types/membro.type'
import type { PagedResponse } from '@/types/pagedResponse.type'
import type { UsuarioResponse } from '@/types/usuario.types'

interface ListarMembrosParams {
  q?: string
  page?: number
  size?: number
}

export const membrosService = {
  listar: (params: ListarMembrosParams): Promise<PagedResponse<MembroResponse>> =>
    api.get<PagedResponse<MembroResponse>>(Endpoints.membros.LISTAR, {
      params: {
        q: params.q || undefined,
        page: params.page ?? 0,
        size: params.size ?? 20,
      },
    }).then(res => res.data),

  buscar: (id: string): Promise<MembroResponse> =>
    api.get<MembroResponse>(Endpoints.membros.BY_ID(id)).then(res => res.data),

  criar: (data: MembroRequest): Promise<MembroResponse> =>
    api.post<MembroResponse>(Endpoints.membros.CRIAR, data).then(res => res.data),

  atualizar: (id: string, data: MembroRequest): Promise<MembroResponse> =>
    api.put<MembroResponse>(Endpoints.membros.BY_ID(id), data).then(res => res.data),

  concederAcesso: (data: ConcederAcessoRequest): Promise<UsuarioResponse> =>
    api.post<UsuarioResponse>(Endpoints.usuarios.CONCEDER_ACESSO, data).then(res => res.data),
  
  reativarAcesso: (data: ConcederAcessoRequest): Promise<UsuarioResponse> =>
    api.post<UsuarioResponse>(Endpoints.usuarios.REATIVAR_ACESSO, data).then(res => res.data),

  arquivar: (id: string): Promise<void> =>
    api.delete(Endpoints.membros.BY_ID(id)).then(() => undefined),
}

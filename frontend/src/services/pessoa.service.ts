import { api } from '@/lib/api'
import { Endpoints } from '@/lib/endpoints'
import type {
  PessoaRequest,
  PessoaResponse,
  PessoaArquivadaResponse,
  ConcederAcessoRequest,
  Vinculo,
} from '@/types/pessoa.type'
import type { PagedResponse } from '@/types/pagedResponse.type'
import type { UsuarioResponse } from '@/types/usuario.types'

interface ListarPessoasParams {
  q?: string
  page?: number
  size?: number
  vinculo?: Vinculo | ''
}

export const pessoasService = {
  listar: (params: ListarPessoasParams): Promise<PagedResponse<PessoaResponse>> =>
    api.get<PagedResponse<PessoaResponse>>(Endpoints.pessoas.LISTAR, {
      params: {
        q: params.q || undefined,
        page: params.page ?? 0,
        size: params.size ?? 20,
        vinculo: params.vinculo || undefined,
      },
    }).then(res => res.data),

  buscar: (id: string): Promise<PessoaResponse> =>
    api.get<PessoaResponse>(Endpoints.pessoas.BY_ID(id)).then(res => res.data),

  buscarMe: (): Promise<PessoaResponse> =>
    api.get<PessoaResponse>(Endpoints.pessoas.ME).then(res => res.data),

  atualizarMe: (data: PessoaRequest): Promise<PessoaResponse> =>
    api.put<PessoaResponse>(Endpoints.pessoas.ME, data).then(res => res.data),

  listarBairros: (): Promise<string[]> =>
    api.get<string[]>(Endpoints.pessoas.BAIRROS).then(res => res.data),

  criar: (data: PessoaRequest): Promise<PessoaResponse> =>
    api.post<PessoaResponse>(Endpoints.pessoas.CRIAR, data).then(res => res.data),

  atualizar: (id: string, data: PessoaRequest): Promise<PessoaResponse> =>
    api.put<PessoaResponse>(Endpoints.pessoas.BY_ID(id), data).then(res => res.data),

  concederAcesso: (data: ConcederAcessoRequest): Promise<UsuarioResponse> =>
    api.post<UsuarioResponse>(Endpoints.usuarios.CONCEDER_ACESSO, data).then(res => res.data),

  reativarAcesso: (data: ConcederAcessoRequest): Promise<UsuarioResponse> =>
    api.post<UsuarioResponse>(Endpoints.usuarios.REATIVAR_ACESSO, data).then(res => res.data),

  reenviarConvite: (usuarioId: string): Promise<void> =>
    api.post(Endpoints.usuarios.REENVIAR_CONVITE(usuarioId)).then(() => undefined),

  arquivar: (id: string): Promise<void> =>
    api.delete(Endpoints.pessoas.BY_ID(id)).then(() => undefined),

  listarArquivadas: (): Promise<PessoaArquivadaResponse[]> =>
    api.get<PessoaArquivadaResponse[]>(Endpoints.pessoas.ARQUIVADOS).then(res => res.data),

  restaurar: (id: string): Promise<void> =>
    api.post(Endpoints.pessoas.RESTAURAR(id)).then(() => undefined),

  excluirDefinitivo: (id: string): Promise<void> =>
    api.delete(Endpoints.pessoas.DEFINITIVO(id)).then(() => undefined),
}

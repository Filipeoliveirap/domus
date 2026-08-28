import { api } from '@/lib/api'
import { Endpoints } from '@/lib/endpoints'
import type {
  MinisterioRequest, MinisterioResponse, MinisterioDetalheResponse,
} from '@/types/ministerio.type'

export const ministerioService = {
  listar: (): Promise<MinisterioResponse[]> =>
    api.get<MinisterioResponse[]>(Endpoints.ministerios.LISTAR).then(res => res.data),

  detalhe: (id: string): Promise<MinisterioDetalheResponse> =>
    api.get<MinisterioDetalheResponse>(Endpoints.ministerios.BY_ID(id)).then(res => res.data),

  criar: (data: MinisterioRequest): Promise<MinisterioResponse> =>
    api.post<MinisterioResponse>(Endpoints.ministerios.CRIAR, data).then(res => res.data),

  atualizar: (id: string, data: MinisterioRequest): Promise<MinisterioResponse> =>
    api.put<MinisterioResponse>(Endpoints.ministerios.BY_ID(id), data).then(res => res.data),

  atualizarFoto: (id: string, fotoId: string | null): Promise<void> =>
    api.patch(Endpoints.ministerios.FOTO(id), { fotoId }).then(() => undefined),

  arquivar: (id: string): Promise<void> =>
    api.delete(Endpoints.ministerios.BY_ID(id)).then(() => undefined),

  listarArquivadas: (): Promise<MinisterioResponse[]> =>
    api.get<MinisterioResponse[]>(Endpoints.ministerios.ARQUIVADOS).then(res => res.data),

  restaurar: (id: string): Promise<void> =>
    api.post(Endpoints.ministerios.RESTAURAR(id)).then(() => undefined),

  excluirDefinitivo: (id: string): Promise<void> =>
    api.delete(Endpoints.ministerios.DEFINITIVO(id)).then(() => undefined),

  adicionarMembro: (id: string, pessoaId: string): Promise<void> =>
    api.post(Endpoints.ministerios.MEMBROS(id), { pessoaId }).then(() => undefined),

  removerMembro: (id: string, pessoaId: string): Promise<void> =>
    api.delete(Endpoints.ministerios.MEMBRO(id, pessoaId)).then(() => undefined),

  atualizarPapel: (id: string, pessoaId: string, papel: 'LIDER' | 'MEMBRO'): Promise<void> =>
    api.put(Endpoints.ministerios.PAPEL(id, pessoaId), { papel }).then(() => undefined),

  pedirEntrada: (id: string): Promise<void> =>
    api.post(Endpoints.ministerios.PEDIDOS(id)).then(() => undefined),

  aceitarPedido: (id: string, pessoaId: string): Promise<void> =>
    api.put(Endpoints.ministerios.ACEITAR_PEDIDO(id, pessoaId)).then(() => undefined),

  recusarPedido: (id: string, pessoaId: string): Promise<void> =>
    api.delete(Endpoints.ministerios.RECUSAR_PEDIDO(id, pessoaId)).then(() => undefined),
}

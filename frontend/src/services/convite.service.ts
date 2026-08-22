import { api } from '@/lib/api'
import { Endpoints } from '@/lib/endpoints'
import type { GerarConviteResponse, ConvitePublico, EntrarConviteRequest } from '@/types/convite.type'
import type { ConvidadoResponse } from '@/types/inscricao.type'

export const conviteService = {
  gerar: (eventoId: string): Promise<GerarConviteResponse> =>
    api.post<GerarConviteResponse>(Endpoints.convites.GERAR(eventoId)).then(res => res.data),

  consultar: (token: string): Promise<ConvitePublico> =>
    api.get<ConvitePublico>(Endpoints.convites.CONSULTAR(token)).then(res => res.data),

  entrar: (token: string, dados: EntrarConviteRequest): Promise<ConvidadoResponse> =>
    api.post<ConvidadoResponse>(Endpoints.convites.ENTRAR(token), dados).then(res => res.data),
}

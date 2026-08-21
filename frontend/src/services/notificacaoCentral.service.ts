import { api } from '@/lib/api'
import { Endpoints } from '@/lib/endpoints'
import type { NotificacaoCentral } from '@/types/notificacaoCentral.type'
import type { PagedResponse } from '@/types/pagedResponse.type'

export const notificacaoCentralService = {
  listar: (): Promise<PagedResponse<NotificacaoCentral>> =>
    api.get<PagedResponse<NotificacaoCentral>>(Endpoints.notificacoes.LISTAR).then((res) => res.data),

  contagemNaoLidas: (): Promise<number> =>
    api.get<{ total: number }>(Endpoints.notificacoes.CONTAGEM_NAO_LIDAS).then((res) => res.data.total),

  marcarLida: (id: string): Promise<void> =>
    api.patch(Endpoints.notificacoes.MARCAR_LIDA(id)).then(() => undefined),

  marcarTodasLidas: (): Promise<void> =>
    api.patch(Endpoints.notificacoes.MARCAR_TODAS_LIDAS).then(() => undefined),
}

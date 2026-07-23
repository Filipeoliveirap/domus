import { api } from '@/lib/api'
import { Endpoints } from '@/lib/endpoints'
import type { LocalEventoRequest, LocalEventoResponse } from '@/types/evento.type'

export const locaisEventoService = {
  listar: (): Promise<LocalEventoResponse[]> =>
    api.get<LocalEventoResponse[]>(Endpoints.locaisEvento.LISTAR).then(res => res.data),

  criar: (data: LocalEventoRequest): Promise<LocalEventoResponse> =>
    api.post<LocalEventoResponse>(Endpoints.locaisEvento.CRIAR, data).then(res => res.data),

  atualizar: (id: string, data: LocalEventoRequest): Promise<LocalEventoResponse> =>
    api.put<LocalEventoResponse>(Endpoints.locaisEvento.BY_ID(id), data).then(res => res.data),

  arquivar: (id: string): Promise<void> =>
    api.delete(Endpoints.locaisEvento.BY_ID(id)).then(() => undefined),
}

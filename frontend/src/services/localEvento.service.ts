import { api } from '@/lib/api'
import { Endpoints } from '@/lib/endpoints'
import type { LocalEventoResponse } from '@/types/evento.type'

export const locaisEventoService = {
  listar: (): Promise<LocalEventoResponse[]> =>
    api.get<LocalEventoResponse[]>(Endpoints.locaisEvento.LISTAR).then(res => res.data),
}

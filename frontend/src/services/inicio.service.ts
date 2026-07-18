import { api } from '@/lib/api'
import { Endpoints } from '@/lib/endpoints'
import type { InicioResponse } from '@/types/inicio.type'

export const inicioService = {
  carregar: (): Promise<InicioResponse> =>
    api.get<InicioResponse>(Endpoints.inicio.GET).then((res) => res.data),
}

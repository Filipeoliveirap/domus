import { api } from '@/lib/api'
import { Endpoints } from '@/lib/endpoints'
import type { CampoPersonalizadoResponse, CampoPersonalizadoRequest } from '@/types/campoPersonalizado.type'

export const camposPersonalizadosService = {
  listar: (eventoId: string): Promise<CampoPersonalizadoResponse[]> =>
    api.get<CampoPersonalizadoResponse[]>(Endpoints.eventos.CAMPOS_PERSONALIZADOS(eventoId)).then(res => res.data),

  salvar: (eventoId: string, dados: CampoPersonalizadoRequest[]): Promise<CampoPersonalizadoResponse[]> =>
    api.put<CampoPersonalizadoResponse[]>(Endpoints.eventos.CAMPOS_PERSONALIZADOS(eventoId), dados)
      .then(res => res.data),
}

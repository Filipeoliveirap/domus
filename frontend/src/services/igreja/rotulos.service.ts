import { api } from '@/lib/api'
import { Endpoints } from '@/lib/endpoints'
import type { RotulosCustomizados, RotulosRequest } from '@/types/igreja/igreja.type'

export const rotulosService = {
  atualizar: async (body: RotulosRequest): Promise<RotulosCustomizados> => {
    const { data } = await api.put(Endpoints.igreja.ROTULOS, body)
    return data
  },
}

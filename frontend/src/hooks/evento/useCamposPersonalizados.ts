import { useQuery } from '@tanstack/react-query'
import { camposPersonalizadosService } from '@/services/campoPersonalizado.service'

export function useCamposPersonalizados(eventoId: string) {
  return useQuery({
    queryKey: ['campos-personalizados', eventoId],
    queryFn: () => camposPersonalizadosService.listar(eventoId),
    enabled: !!eventoId,
  })
}

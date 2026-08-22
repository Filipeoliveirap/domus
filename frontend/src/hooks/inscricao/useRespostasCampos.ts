import { useQuery } from '@tanstack/react-query'
import { inscricoesService } from '@/services/inscricao.service'

export function useRespostasCampos(inscricaoId: string, acompanhanteId?: string) {
  return useQuery({
    queryKey: ['respostas-campos', inscricaoId, acompanhanteId ?? null],
    queryFn: () => inscricoesService.respostas(inscricaoId, acompanhanteId),
    enabled: !!inscricaoId,
  })
}

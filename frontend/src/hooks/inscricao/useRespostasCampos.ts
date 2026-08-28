import { useQuery } from '@tanstack/react-query'
import { inscricoesService } from '@/services/inscricao.service'

export function useRespostasCampos(inscricaoId: string) {
  return useQuery({
    queryKey: ['respostas-campos', inscricaoId],
    queryFn: () => inscricoesService.respostas(inscricaoId),
    enabled: !!inscricaoId,
  })
}

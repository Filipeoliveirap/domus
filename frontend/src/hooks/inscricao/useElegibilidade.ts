import { useQuery } from '@tanstack/react-query'
import { inscricoesService } from '@/services/inscricao.service'

/**
 * Prévia de elegibilidade da PRÓPRIA PESSOA logada para o evento — conveniência de UX
 * para desabilitar o botão de inscrição com o motivo ao lado, ANTES de tentar o POST.
 *
 * <p>NUNCA é defesa: quem chama o POST direto esbarra no mesmo 422 do backend, e é ESSE
 * 422 quem decide de verdade (ex.: última vaga tomada entre carregar a tela e clicar).
 */
export function useElegibilidade(eventoId: string | undefined) {
  return useQuery({
    queryKey: ['elegibilidade', eventoId],
    queryFn: () => inscricoesService.elegibilidade(eventoId!),
    enabled: !!eventoId,
  })
}

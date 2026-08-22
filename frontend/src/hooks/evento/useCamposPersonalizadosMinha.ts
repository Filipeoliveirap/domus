import { useQuery } from '@tanstack/react-query'
import { camposPersonalizadosService } from '@/services/campoPersonalizado.service'

/** Só pra fluxo de auto-resposta (a própria pessoa logada) — pula campo mapeado que ela já
 *  tem no cadastro. Nunca usar pra responder em nome de acompanhante/convidado. */
export function useCamposPersonalizadosMinha(eventoId: string) {
  return useQuery({
    queryKey: ['campos-personalizados-minha', eventoId],
    queryFn: () => camposPersonalizadosService.listarParaMinhaResposta(eventoId),
    enabled: !!eventoId,
  })
}

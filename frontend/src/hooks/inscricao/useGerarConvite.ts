import { useQuery } from '@tanstack/react-query'
import { conviteService } from '@/services/convite.service'

/** `useQuery`, não `useMutation`: precisa disparar sozinho assim que `habilitado` vira
 *  true (a pessoa já está inscrita, ou acabou de responder que sim/não quer participar) —
 *  disparar um POST a partir de um useEffect+mutate ficava vulnerável ao "mount, unmount,
 *  remount" de efeitos do StrictMode (dev): duas chamadas reais chegavam a acontecer no
 *  servidor, mas o componente que ficava montado no fim nunca via o resultado, preso pra
 *  sempre em "Gerando link…". `useQuery` com `enabled` é o padrão certo pra "buscar quando
 *  a condição for verdadeira" — o próprio React Query dedupe e cuida do ciclo de vida. */
export function useGerarConvite(eventoId: string, habilitado: boolean) {
  return useQuery({
    queryKey: ['convite-compartilhar', eventoId],
    queryFn: () => conviteService.gerar(eventoId),
    enabled: habilitado,
    staleTime: Infinity,
    retry: false,
  })
}

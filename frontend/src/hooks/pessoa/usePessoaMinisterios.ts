import { useQuery } from '@tanstack/react-query'
import { api } from '@/lib/api'
import { Endpoints } from '@/lib/endpoints'
import type { MinisterioResponse } from '@/types/ministerio.type'

export function usePessoaMinisterios(pessoaId: string) {
  return useQuery({
    queryKey: ['pessoas', pessoaId, 'ministerios'],
    queryFn: () =>
      api.get<MinisterioResponse[]>(Endpoints.pessoas.PESSOA_MINISTERIOS(pessoaId))
        .then(res => res.data),
    enabled: !!pessoaId,
  })
}

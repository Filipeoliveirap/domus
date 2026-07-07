// hooks/membro/useMembro.ts
import { useQuery, keepPreviousData } from '@tanstack/react-query'
import { membrosService } from '@/services/membro.service'


interface UseMembrosParams {
  q: string
  page: number
  size?: number
}
export function useMembros({ q, page, size = 20 }: UseMembrosParams) {
  return useQuery({
    queryKey: ['membros', { q, page, size }],
    queryFn: () => membrosService.listar({ q, page, size }),
    placeholderData: keepPreviousData,
  })
}
import { useQuery } from '@tanstack/react-query'
import { usuarioService } from '@/services/usuarios.service'

export function useUsuario(id: string) {
  return useQuery({
    queryKey: ['usuario', id],
    queryFn: () => usuarioService.buscarUsuario(id),
    enabled: !!id,
  })
}
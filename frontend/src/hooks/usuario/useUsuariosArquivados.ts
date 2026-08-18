import { useQuery } from '@tanstack/react-query'
import { usuarioService } from '@/services/usuarios.service'

export function useUsuariosArquivados() {
  return useQuery({
    queryKey: ['usuarios-arquivados'],
    queryFn: () => usuarioService.listarArquivados(),
  })
}

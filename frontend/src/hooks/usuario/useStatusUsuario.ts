import { useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { usuarioService } from '@/services/usuarios.service'
import { UsuarioResponse } from '@/types/usuario.types'
import axios from 'axios'
import type { ApiError } from '@/types/api.types'
import { toast } from 'sonner'

export function useStatusUsuario(usuario: UsuarioResponse, onClose: () => void) {
  const queryClient = useQueryClient()
  const [erroGeral, setErroGeral] = useState<string | null>(null)
  const [isLoading, setIsLoading] = useState(false)
  const novoStatus = !usuario.ativo 

  const confirmar = async () => {
    setErroGeral(null); setIsLoading(true)
    try {
      await usuarioService.atualizarStatus(usuario.id, novoStatus)
      queryClient.invalidateQueries({ queryKey: ['usuarios'] })
      toast.success(novoStatus ? 'Usuário reativado com sucesso!' : 'Usuário desativado com sucesso!')
      onClose()
    } catch (error: unknown) {
      if (axios.isAxiosError<ApiError>(error)) {
        const e = error.response?.data
        if (e?.error === 'ULTIMO_ADMIN') { setErroGeral(e.message); return }
        setErroGeral(e?.message ?? 'Erro ao alterar o status. Tente novamente.')
      } else setErroGeral('Erro ao alterar o status. Tente novamente.')
    } finally { setIsLoading(false) }
  }

  return { confirmar, isLoading, erroGeral, novoStatus }
}
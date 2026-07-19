import { useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { usuarioService } from '@/services/usuarios.service'
import { UsuarioResponse } from '@/types/usuario.types'
import axios from 'axios'
import type { ApiError } from '@/types/api.types'
import { notificar } from '@/components/common/Notificacao/notificar'

export function useArquivarUsuario(usuario: UsuarioResponse, onClose: () => void) {
  const queryClient = useQueryClient()
  const [erroGeral, setErroGeral] = useState<string | null>(null)
  const [isLoading, setIsLoading] = useState(false)

  const confirmar = async () => {
    setErroGeral(null); setIsLoading(true)
    try {
      await usuarioService.arquivarUsuario(usuario.id)
      queryClient.invalidateQueries({ queryKey: ['usuarios'] })
      notificar.sucesso('Usuário arquivado com sucesso!')
      onClose()
    } catch (error: unknown) {
      if (axios.isAxiosError<ApiError>(error)) {
        const e = error.response?.data
        if (e?.error === 'ULTIMO_ADMIN') { setErroGeral(e.message); return }
        setErroGeral(e?.message ?? 'Erro ao arquivar o usuário. Tente novamente.')
      } else setErroGeral('Erro ao arquivar o usuário. Tente novamente.')
    } finally { setIsLoading(false) }
  }

  return { confirmar, isLoading, erroGeral }
}
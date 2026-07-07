import { useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { usuarioService } from '@/services/usuarios.service'
import { UsuarioResponse, Role } from '@/types/usuario.types'
import { useAuthStore } from '@/store/authStore'
import axios from 'axios'
import type { ApiError } from '@/types/api.types'
import { toast } from 'sonner'

export function usePermissaoUsuario(usuario: UsuarioResponse, onClose: () => void) {
  const queryClient = useQueryClient()
  const idLogado = useAuthStore((s) => s.id)
  const atualizarUsuarioLogado = useAuthStore((s) => s.atualizarUsuarioLogado)

  const [roleSelecionada, setRoleSelecionada] = useState<Role>(usuario.role as Role)
  const [erroGeral, setErroGeral] = useState<string | null>(null)
  const [isLoading, setIsLoading] = useState(false)

  const semMudanca = roleSelecionada === usuario.role

  const salvar = async () => {
    setErroGeral(null); setIsLoading(true)
    try {
      const atualizado = await usuarioService.atualizarRole(usuario.id, roleSelecionada)
      queryClient.invalidateQueries({ queryKey: ['usuarios'] })
      if (idLogado === usuario.id) {
        atualizarUsuarioLogado({ role: atualizado.role as Role })
      }
      toast.success('Permissões atualizadas com sucesso!')
      onClose()
    } catch (error: unknown) {
      if (axios.isAxiosError<ApiError>(error)) {
        const e = error.response?.data
        if (e?.error === 'ULTIMO_ADMIN') { setErroGeral(e.message); return }
        setErroGeral(e?.message ?? 'Erro ao alterar permissões. Tente novamente.')
      } else setErroGeral('Erro ao alterar permissões. Tente novamente.')
    } finally { setIsLoading(false) }
  }

  return { roleSelecionada, setRoleSelecionada, salvar, isLoading, erroGeral, semMudanca }
}
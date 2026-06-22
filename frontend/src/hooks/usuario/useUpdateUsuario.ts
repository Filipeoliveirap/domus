
import { useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { zodResolver } from '@hookform/resolvers/zod'
import { useAppForm } from '../forms/useAppForm'
import { EditarUsuarioFormData, editarUsuarioSchema } from '@/lib/validators'
import { usuarioService } from '@/services/usuarios.service'
import { UsuarioResponse } from '@/types/usuario.types'
import axios from 'axios'
import type { ApiError } from '@/types/api.types'
import { toast } from 'sonner'
import { useAuthStore } from '@/store/authStore'
import type { Role } from '@/types/usuario.types'

export function useEditarUsuario(usuario: UsuarioResponse, onClose: () => void) {
  const queryClient = useQueryClient()
  const [erroGeral, setErroGeral] = useState<string | null>(null)
  const [isLoading, setIsLoading] = useState(false)
  const idLogado = useAuthStore((s) => s.id)
  const atualizarUsuarioLogado = useAuthStore((s) => s.atualizarUsuarioLogado)

  const {
    register, handleSubmit, control, watch, setError,
    formState: { errors }, isFormIncomplete,
  } = useAppForm<EditarUsuarioFormData>({
    resolver: zodResolver(editarUsuarioSchema),
    defaultValues: {
      nome: usuario.nome,
      email: usuario.email,
      role: usuario.role as EditarUsuarioFormData['role'],
    },
    requiredFields: ['nome', 'email', 'role'],
  })

  const onSubmit = async (data: EditarUsuarioFormData) => {
    setErroGeral(null)
    setIsLoading(true)
    try {
      const atualizado = await usuarioService.atualizarUsuario(usuario.id, data)
      queryClient.invalidateQueries({ queryKey: ['usuarios'] })
      if (idLogado === usuario.id) {
        atualizarUsuarioLogado({ nome: atualizado.nome, role: atualizado.role as Role })
      }

      toast.success('Usuário atualizado com sucesso!')
      onClose()
    } catch (error: unknown) {
      if (axios.isAxiosError<ApiError>(error)) {
        const errorData = error.response?.data
        if (errorData?.error === 'EMAIL_DUPLICADO') {
          setError('email', { type: 'server', message: errorData.message })
          return
        }
        if (errorData?.error === 'ULTIMO_ADMIN') {
          setErroGeral(errorData.message) 
          return
        }
        setErroGeral(errorData?.message ?? 'Erro ao atualizar usuário. Tente novamente.')
      } else {
        setErroGeral('Erro ao atualizar usuário. Tente novamente.')
      }
    } finally {
      setIsLoading(false)
    }
  }

  return { register, handleSubmit, control, watch, setError, errors, isFormIncomplete, erroGeral, isLoading, onSubmit }
}
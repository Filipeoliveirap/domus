import { useState } from 'react'
import axios from 'axios'
import { toast } from 'sonner'
import { useQueryClient } from '@tanstack/react-query'
import { membrosService } from '@/services/membro.service'
import type { MembroResponse, ConcederAcessoRequest } from '@/types/membro.type'
import type { ApiError } from '@/types/api.types'

type DadosAcesso = Omit<ConcederAcessoRequest, 'membroId'>

export function useConcederAcesso(membro: MembroResponse, onClose: () => void) {
  const [erroGeral, setErroGeral] = useState<string | null>(null)
  const [isLoading, setIsLoading] = useState(false)
  const [precisaReativar, setPrecisaReativar] = useState(false)
  const [dadosPendentes, setDadosPendentes] = useState<DadosAcesso | null>(null)
  const queryClient = useQueryClient()

  function invalidar() {
    queryClient.invalidateQueries({ queryKey: ['usuarios'] })
    queryClient.invalidateQueries({ queryKey: ['membros'] })
  }

  const confirmar = async (dados: DadosAcesso) => {
    setErroGeral(null)
    setIsLoading(true)
    try {
      await membrosService.concederAcesso({ membroId: membro.id, ...dados })
      invalidar()
      toast.success(`Acesso concedido a ${membro.nome}.`)
      onClose()
    } catch (error: unknown) {
      if (axios.isAxiosError<ApiError>(error)) {
        const data = error.response?.data
        if (data?.error === 'MEMBRO_TEM_USUARIO_ARQUIVADO') {
          setDadosPendentes(dados)  
          setPrecisaReativar(true)   
          return
        }
        setErroGeral(data?.message ?? 'Não foi possível conceder acesso. Tente novamente.')
      } else {
        setErroGeral('Não foi possível conceder acesso. Tente novamente.')
      }
    } finally {
      setIsLoading(false)
    }
  }

  const reativar = async () => {
    if (!dadosPendentes) return
    setErroGeral(null)
    setIsLoading(true)
    try {
      await membrosService.reativarAcesso({ membroId: membro.id, ...dadosPendentes })
      invalidar()
      toast.success(`Acesso de ${membro.nome} reativado.`)
      onClose()
    } catch (error: unknown) {
      if (axios.isAxiosError<ApiError>(error)) {
        setErroGeral(error.response?.data?.message ?? 'Não foi possível reativar. Tente novamente.')
      } else {
        setErroGeral('Não foi possível reativar. Tente novamente.')
      }
    } finally {
      setIsLoading(false)
    }
  }

  const cancelarReativacao = () => {
    setPrecisaReativar(false)
    setDadosPendentes(null)
    setErroGeral(null)
  }

  return {
    confirmar,
    reativar,
    cancelarReativacao,
    precisaReativar,
    isLoading,
    erroGeral,
  }
}
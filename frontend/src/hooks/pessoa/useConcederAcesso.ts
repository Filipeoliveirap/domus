import { useState } from 'react'
import axios from 'axios'
import { notificar } from '@/components/common/Notificacao/notificar'
import { useQueryClient } from '@tanstack/react-query'
import { invalidarCache } from '@/lib/cacheInvalidacao'
import { pessoasService } from '@/services/pessoa.service'
import type { PessoaResponse, ConcederAcessoRequest } from '@/types/pessoa.type'
import type { ApiError } from '@/types/api.types'

type DadosAcesso = Omit<ConcederAcessoRequest, 'pessoaId'>

export function useConcederAcesso(pessoa: PessoaResponse, onClose: () => void) {
  const [erroGeral, setErroGeral] = useState<string | null>(null)
  const [isLoading, setIsLoading] = useState(false)
  const [precisaReativar, setPrecisaReativar] = useState(false)
  const [dadosPendentes, setDadosPendentes] = useState<DadosAcesso | null>(null)
  const queryClient = useQueryClient()

  function invalidar() {
    invalidarCache(queryClient, 'usuario', 'pessoa')
  }

  const confirmar = async (dados: DadosAcesso) => {
    setErroGeral(null)
    setIsLoading(true)
    try {
      await pessoasService.concederAcesso({ pessoaId: pessoa.id, ...dados })
      invalidar()
      notificar.sucesso(`Convite enviado para ${pessoa.nome}.`)
      onClose()
    } catch (error: unknown) {
      if (axios.isAxiosError<ApiError>(error)) {
        const data = error.response?.data
        // Código de erro herdado do backend (`UsuarioService`), não renomeado por lá.
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
      await pessoasService.reativarAcesso({ pessoaId: pessoa.id, ...dadosPendentes })
      invalidar()
      notificar.sucesso(`Convite reenviado para ${pessoa.nome}.`)
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

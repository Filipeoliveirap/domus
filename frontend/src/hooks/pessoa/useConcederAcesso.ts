import { useState } from 'react'
import axios from 'axios'
import { notificar } from '@/components/common/Notificacao/notificar'
import { useQueryClient } from '@tanstack/react-query'
import { invalidarCache } from '@/lib/cacheInvalidacao'
import { pessoasService } from '@/services/pessoa.service'
import { usuarioService } from '@/services/usuarios.service'
import type { PessoaResponse, ConcederAcessoRequest } from '@/types/pessoa.type'
import type { ApiError } from '@/types/api.types'

type DadosAcesso = Omit<ConcederAcessoRequest, 'pessoaId'>

export function useConcederAcesso(pessoa: PessoaResponse, onClose: () => void) {
  const [erroGeral, setErroGeral] = useState<string | null>(null)
  const [isLoading, setIsLoading] = useState(false)
  const [precisaReativar, setPrecisaReativar] = useState(false)
  const [dadosPendentes, setDadosPendentes] = useState<(DadosAcesso & { capacidades?: string[] }) | null>(null)
  const queryClient = useQueryClient()

  function invalidar() {
    invalidarCache(queryClient, 'usuario', 'pessoa')
  }

  async function salvarCapacidades(usuarioId: string, capacidades: string[]) {
    for (const cap of capacidades) {
      try { await usuarioService.concederCapacidade(usuarioId, cap) } catch { /* silencioso */ }
    }
  }

  const confirmar = async (dados: DadosAcesso & { capacidades?: string[] }) => {
    setErroGeral(null)
    setIsLoading(true)
    try {
      const { capacidades, ...dadosLimpos } = dados
      const criado = await pessoasService.concederAcesso({ pessoaId: pessoa.id, ...dadosLimpos })
      if (capacidades?.length) {
        await salvarCapacidades(criado.id, capacidades)
      }
      invalidar()
      notificar.sucesso(`Convite enviado para ${pessoa.nome}.`)
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
      const { capacidades: caps, ...dadosLimpos } = dadosPendentes
      const criado = await pessoasService.reativarAcesso({ pessoaId: pessoa.id, ...dadosLimpos })
      if (caps?.length) {
        await salvarCapacidades(criado.id, caps)
      }
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

  return { confirmar, reativar, cancelarReativacao, precisaReativar, isLoading, erroGeral }
}

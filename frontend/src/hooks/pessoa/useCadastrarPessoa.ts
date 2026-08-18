// hooks/pessoa/useCadastrarPessoa.ts
import { useRouter } from 'next/navigation'
import { useState, useEffect } from 'react'
import axios from 'axios'
import { notificar } from '@/components/common/Notificacao/notificar'
import { useQueryClient } from '@tanstack/react-query'
import { invalidarCache } from '@/lib/cacheInvalidacao'
import { zodResolver } from '@hookform/resolvers/zod'
import { useAppForm } from '../forms/useAppForm'
import { PessoaFormInput, pessoaSchema, type PessoaFormData } from '@/lib/validators'
import { pessoasService } from '@/services/pessoa.service'
import { ministerioService } from '@/services/ministerio.service'
import { usePessoaMinisterios } from './usePessoaMinisterios'
import { formatarTelefone } from '@/lib/masks'
import type { PessoaRequest, PessoaResponse } from '@/types/pessoa.type'
import type { ApiError } from '@/types/api.types'

interface UsePessoaFormParams {
  pessoaId?: string
  pessoaInicial?: PessoaResponse
}

export function useCadastrarPessoa({ pessoaId, pessoaInicial }: UsePessoaFormParams = {}) {
  const router = useRouter()
  const [erroGeral, setErroGeral] = useState<string | null>(null)
  const [isLoading, setIsLoading] = useState(false)
  const queryClient = useQueryClient()
  const ehEdicao = !!pessoaId

  const form = useAppForm<PessoaFormInput, PessoaFormData>({
    resolver: zodResolver(pessoaSchema),
    defaultValues: {
      nome: '', email: '', telefone: '', dataNascimento: '',
      endereco: { cep: '', logradouro: '', numero: '', complemento: '', bairro: '', cidade: '', uf: '' },
      vinculo: 'CONGREGANTE', estadoCivil: '', sexo: '',
      cargo: '', observacoes: '',
      fotoId: null,
    },
    requiredFields: ['nome'],
  })

  const { reset } = form

  // Redes atuais da pessoa (edição) — semente do seletor. Cadastro novo começa vazio: sem
  // pessoaId ainda, não tem como vincular a rede até a pessoa existir de verdade.
  const { data: redesAtuais } = usePessoaMinisterios(pessoaId ?? '')
  const [redesSelecionadas, setRedesSelecionadas] = useState<Set<string>>(new Set())

  useEffect(() => {
    if (redesAtuais) {
      setRedesSelecionadas(new Set(redesAtuais.map((r) => r.id)))
    }
  }, [redesAtuais])

  // preenche o form quando os dados da pessoa chegam (edição)
  useEffect(() => {
    if (pessoaInicial) {
      reset({
        nome: pessoaInicial.nome,
        email: pessoaInicial.email ?? '',
        telefone: pessoaInicial.telefone ? formatarTelefone(pessoaInicial.telefone) : '',
        dataNascimento: pessoaInicial.dataNascimento ?? '',
        endereco: {
          cep: pessoaInicial.endereco?.cep ?? '',
          logradouro: pessoaInicial.endereco?.logradouro ?? '',
          numero: pessoaInicial.endereco?.numero ?? '',
          complemento: pessoaInicial.endereco?.complemento ?? '',
          bairro: pessoaInicial.endereco?.bairro ?? '',
          cidade: pessoaInicial.endereco?.cidade ?? '',
          uf: pessoaInicial.endereco?.uf ?? '',
        },
        vinculo: pessoaInicial.vinculo,
        estadoCivil: pessoaInicial.estadoCivil ?? '',
        sexo: pessoaInicial.sexo ?? '',
        cargo: pessoaInicial.cargo ?? '',
        observacoes: pessoaInicial.observacoes ?? '',
        fotoId: pessoaInicial.fotoId ?? null,
      })
    }
  }, [pessoaInicial, reset])

  const onSubmit = async (data: PessoaFormData) => {
    setErroGeral(null)
    setIsLoading(true)
    try {
      const payload: PessoaRequest = {
        ...data,
        telefone: data.telefone?.replace(/\D/g, '') || undefined,
        estadoCivil: data.estadoCivil || undefined,
        sexo: data.sexo || undefined,
      }

      let pessoaSalvaId: string
      if (ehEdicao) {
        await pessoasService.atualizar(pessoaId!, payload)
        pessoaSalvaId = pessoaId!
        invalidarCache(queryClient, 'pessoa')
        queryClient.invalidateQueries({ queryKey: ['pessoa', pessoaId] })
        notificar.sucesso('Pessoa atualizada com sucesso!')
      } else {
        const criada = await pessoasService.criar(payload)
        pessoaSalvaId = criada.id
        invalidarCache(queryClient, 'pessoa')
        notificar.sucesso('Pessoa cadastrada com sucesso!')
      }

      // Falha ao sincronizar rede não desfaz o cadastro/edição da pessoa (já salvos com
      // sucesso) — só avisa separadamente, sem travar a navegação.
      try {
        await sincronizarRedes(pessoaSalvaId, redesAtuais?.map((r) => r.id) ?? [], [...redesSelecionadas])
        invalidarCache(queryClient, 'ministerio')
      } catch {
        notificar.erro('Pessoa salva, mas não foi possível atualizar as redes dela.')
      }

      router.back()
    } catch (error: unknown) {
      if (axios.isAxiosError<ApiError>(error)) {
        const e = error.response?.data
        if (e?.error === 'EMAIL_DUPLICADO') {
          form.setError('email', { type: 'server', message: e.message })
          return
        }
        setErroGeral(e?.message ?? 'Erro ao salvar pessoa. Tente novamente.')
      } else {
        setErroGeral('Erro ao salvar pessoa. Tente novamente.')
      }
    } finally {
      setIsLoading(false)
    }
  }

  return {
    ...form, onSubmit, erroGeral, isLoading, ehEdicao,
    redesSelecionadas, setRedesSelecionadas,
  }
}

/** Diferença entre o que a pessoa já tinha e o que foi selecionado no formulário — só
 * chama a API para as redes que realmente mudaram (entrar/sair), nunca para as que
 * permaneceram como estavam. */
async function sincronizarRedes(pessoaId: string, idsAntes: string[], idsDepois: string[]) {
  const antesSet = new Set(idsAntes)
  const depoisSet = new Set(idsDepois)

  const paraAdicionar = idsDepois.filter((id) => !antesSet.has(id))
  const paraRemover = idsAntes.filter((id) => !depoisSet.has(id))

  await Promise.all([
    ...paraAdicionar.map((ministerioId) => ministerioService.adicionarMembro(ministerioId, pessoaId)),
    ...paraRemover.map((ministerioId) => ministerioService.removerMembro(ministerioId, pessoaId)),
  ])
}

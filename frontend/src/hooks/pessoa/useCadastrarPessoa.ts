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

      if (ehEdicao) {
        await pessoasService.atualizar(pessoaId!, payload)
        invalidarCache(queryClient, 'pessoa')
        queryClient.invalidateQueries({ queryKey: ['pessoa', pessoaId] })
        notificar.sucesso('Pessoa atualizada com sucesso!')
      } else {
        await pessoasService.criar(payload)
        invalidarCache(queryClient, 'pessoa')
        notificar.sucesso('Pessoa cadastrada com sucesso!')
      }
      router.push('/pessoas')
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

  return { ...form, onSubmit, erroGeral, isLoading, ehEdicao }
}

import { useEffect, useState } from 'react'
import axios from 'axios'
import { zodResolver } from '@hookform/resolvers/zod'
import { notificar } from '@/components/common/Notificacao/notificar'
import { useAppForm } from '../forms/useAppForm'
import { pessoaSchema, type PessoaFormInput, type PessoaFormData } from '@/lib/validators'
import { useAtualizarMinhaPessoa } from './useMinhaPessoa'
import { formatarTelefone, formatarCep } from '@/lib/masks'
import { useAuthStore } from '@/store/authStore'
import type { PessoaRequest, PessoaResponse } from '@/types/pessoa.type'
import type { ApiError } from '@/types/api.types'

/**
 * Formulário da página /perfil. Mesmo padrão de `usePessoaForm` (useAppForm +
 * zodResolver(pessoaSchema) + reset() no useEffect), mas usa
 * `useAtualizarMinhaPessoa` (endpoint /pessoas/me) no lugar de
 * `usePessoa`/`pessoasService.atualizar`: aqui a pessoa edita a SI mesma, não
 * um registro qualquer identificado por id.
 */
export function usePerfilForm(pessoaInicial: PessoaResponse | undefined) {
  const [erroGeral, setErroGeral] = useState<string | null>(null)
  const { mutateAsync, isPending } = useAtualizarMinhaPessoa()

  const form = useAppForm<PessoaFormInput, PessoaFormData>({
    resolver: zodResolver(pessoaSchema),
    defaultValues: {
      nome: '', email: '', telefone: '', dataNascimento: '',
      endereco: { cep: '', logradouro: '', numero: '', complemento: '', bairro: '', cidade: '', uf: '' },
      vinculo: 'CONGREGANTE', estadoCivil: '', sexo: '',
      ministerio: '', cargo: '', observacoes: '', dataBatismo: '', fotoId: null,
    },
    requiredFields: ['nome'],
  })

  const { reset } = form

  useEffect(() => {
    if (pessoaInicial) {
      reset({
        nome: pessoaInicial.nome,
        email: pessoaInicial.email ?? '',
        telefone: pessoaInicial.telefone ? formatarTelefone(pessoaInicial.telefone) : '',
        dataNascimento: pessoaInicial.dataNascimento ?? '',
        endereco: {
          cep: pessoaInicial.endereco?.cep ? formatarCep(pessoaInicial.endereco.cep) : '',
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
        ministerio: pessoaInicial.ministerio ?? '',
        cargo: pessoaInicial.cargo ?? '',
        observacoes: pessoaInicial.observacoes ?? '',
        dataBatismo: pessoaInicial.dataBatismo ?? '',
        fotoId: pessoaInicial.fotoId ?? null,
      })
    }
  }, [pessoaInicial, reset])

  const onSubmit = async (data: PessoaFormData) => {
    setErroGeral(null)
    try {
      const payload: PessoaRequest = {
        ...data,
        telefone: data.telefone?.replace(/\D/g, '') || undefined,
        estadoCivil: data.estadoCivil || undefined,
        sexo: data.sexo || undefined,
        endereco: { ...data.endereco, cep: data.endereco?.cep?.replace(/\D/g, '') || undefined },
        // dataBatismo só faz sentido para MEMBRO — a UI esconde o campo p/ CONGREGANTE,
        // e o payload reforça a regra no envio.
        dataBatismo: data.vinculo === 'MEMBRO' ? (data.dataBatismo || undefined) : undefined,
      }
      await mutateAsync(payload)
      useAuthStore.getState().atualizarUsuarioLogado({ cargo: data.cargo || null })
      notificar.sucesso('Perfil atualizado!')
    } catch (error: unknown) {
      if (axios.isAxiosError<ApiError>(error)) {
        setErroGeral(error.response?.data?.message ?? 'Erro ao salvar. Tente novamente.')
      } else {
        setErroGeral('Erro ao salvar. Tente novamente.')
      }
    }
  }

  return { ...form, onSubmit, erroGeral, isLoading: isPending }
}

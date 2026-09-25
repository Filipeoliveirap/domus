import { describe, it, expect, vi } from 'vitest'
import { renderHook, act } from '@testing-library/react'
import { useCadastrarPessoa } from '../useCadastrarPessoa'
import { pessoasService } from '@/services/pessoa.service'
import axios from 'axios'

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn() }),
}))

vi.mock('@tanstack/react-query', () => ({
  useQueryClient: () => ({ invalidateQueries: vi.fn() }),
}))

vi.mock('@/services/pessoa.service', () => ({
  pessoasService: {
    cadastrar: vi.fn(),
  },
}))

describe('useCadastrarPessoa', () => {
  it('exibe erro de limite excedido do plano quando o backend retorna 402/PLANO_LIMITE_EXCEDIDO', async () => {
    const errorResponse = {
      response: {
        status: 402,
        data: {
          error: 'PLANO_LIMITE_EXCEDIDO',
          message: 'Sua família de igrejas atingiu o limite de 60 pessoas do plano Básico.',
        },
      },
      isAxiosError: true,
    }

    vi.mocked(axios.isAxiosError).mockReturnValue(true)
    vi.mocked(pessoasService.cadastrar).mockRejectedValueOnce(errorResponse)

    const { result } = renderHook(() => useCadastrarPessoa())

    await act(async () => {
      await result.current.onSubmit({
        nome: 'João Silva',
        email: '',
        telefone: '',
        dataNascimento: '',
        endereco: { cep: '', logradouro: '', numero: '', complemento: '', bairro: '', cidade: '', uf: '' },
        vinculo: 'CONGREGANTE',
        estadoCivil: '',
        sexo: '',
        cargo: '',
        observacoes: '',
        fotoId: null,
      })
    })

    expect(result.current.erroGeral).toBe('Sua família de igrejas atingiu o limite de 60 pessoas do plano Básico.')
  })
})

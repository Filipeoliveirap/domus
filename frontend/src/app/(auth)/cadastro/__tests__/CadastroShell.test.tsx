import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { CadastroShell } from '../CadastroShell'

vi.mock('@react-oauth/google', () => ({
  GoogleLogin: () => <div>Google Login Mock</div>,
}))

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn(), back: vi.fn() }),
  useSearchParams: () => new URLSearchParams(),
}))

describe('CadastroShell', () => {
  const baseMockHook: any = {
    passo: 1,
    direcaoPasso: 1,
    totalPassos: 4,
    googleData: null,
    erroGeral: null,
    isLoading: false,
    register: vi.fn(),
    handleSubmit: vi.fn(),
    setValue: vi.fn(),
    errors: {},
    irParaPasso2: vi.fn(),
    voltarParaPasso1: vi.fn(),
    irParaPasso3: vi.fn(),
    voltarParaPasso2: vi.fn(),
    onSubmitCheckout: vi.fn(),
    register2: vi.fn(),
    handleSubmit2: vi.fn(),
    watch2: vi.fn(),
    errors2: {},
    onGoogleAuth: vi.fn(),
    onGoogleError: vi.fn(),
    onSubmitGoogle: vi.fn(),
    aceitouTermosGoogle: false,
    setAceitouTermosGoogle: vi.fn(),
    planoId: 'PRO',
    codigoConvite: null,
    matrizNome: null,
    dadosSucesso: null,
    irParaPessoas: vi.fn(),
    irParaPerfilIgreja: vi.fn(),
    irParaMeuPerfil: vi.fn(),
    irParaPainelInicial: vi.fn(),
  }

  it('renderiza o Passo 1 inicialmente com título de dados da igreja', () => {
    render(<CadastroShell {...baseMockHook} passo={1} />)
    expect(screen.getByRole('heading', { name: /Cadastre sua igreja/i })).toBeInTheDocument()
  })

  it('renderiza o Passo 2 com título de conta de administrador', () => {
    render(<CadastroShell {...baseMockHook} passo={2} />)
    expect(screen.getByRole('heading', { name: /Crie sua conta de administrador/i })).toBeInTheDocument()
  })

  it('renderiza o Passo 3 no modo Matriz com ativacao de 14 dias de teste', () => {
    render(<CadastroShell {...baseMockHook} passo={3} planoId="PRO" codigoConvite={null} />)
    expect(screen.getByRole('heading', { name: /Ative seus 14 dias de teste/i })).toBeInTheDocument()
    expect(screen.getByText(/Plano Pro/i)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /Começar 14 dias grátis/i })).toBeInTheDocument()
  })

  it('renderiza o Passo 3 no modo Filha com vinculo da matriz e sem campos de cartao', () => {
    render(
      <CadastroShell
        {...baseMockHook}
        passo={3}
        codigoConvite="DOMUS-TEST12"
        matrizNome="Igreja Matriz Central"
      />
    )
    expect(screen.getByRole('heading', { name: /Confirme o vínculo da igreja/i })).toBeInTheDocument()
    expect(screen.getByText(/Igreja Matriz Central/i)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /Concluir cadastro gratuitamente/i })).toBeInTheDocument()
  })

  it('renderiza o Passo 4 com comemoracao de igreja criada e atalhos', () => {
    render(
      <CadastroShell
        {...baseMockHook}
        passo={4}
        dadosSucesso={{ nome: 'Pastor Marcos', nomeIgreja: 'Igreja Nova Vida' }}
      />
    )
    expect(screen.getByRole('heading', { name: /Igreja criada/i })).toBeInTheDocument()
    expect(screen.getByText(/Igreja Nova Vida/i)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /Cadastrar pessoas/i })).toBeInTheDocument()
  })
})

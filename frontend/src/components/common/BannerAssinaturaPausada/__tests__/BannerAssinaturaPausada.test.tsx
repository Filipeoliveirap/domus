import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { BannerAssinaturaPausada } from '../BannerAssinaturaPausada'
import { useAuthStore } from '@/store/authStore'

vi.mock('@/store/authStore', () => ({
  useAuthStore: vi.fn(),
}))

describe('BannerAssinaturaPausada', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('nao renderiza nada quando statusAssinatura nao e PAUSADA', () => {
    vi.mocked(useAuthStore).mockImplementation((selector: any) =>
      selector({ statusAssinatura: 'ATIVA', role: { nome: 'ADMIN_IGREJA' } })
    )

    const { container } = render(<BannerAssinaturaPausada />)
    expect(container.firstChild).toBeNull()
  })

  it('renderiza aviso de leitura apenas e botao de atualizar cartao quando e ADMIN_IGREJA e statusAssinatura e PAUSADA', () => {
    vi.mocked(useAuthStore).mockImplementation((selector: any) =>
      selector({ statusAssinatura: 'PAUSADA', role: { nome: 'ADMIN_IGREJA' } })
    )

    render(<BannerAssinaturaPausada />)

    expect(screen.getByText(/Assinatura Pausada por Pendência de Pagamento/i)).toBeInTheDocument()
    expect(screen.getByText(/O sistema está operando em modo de leitura apenas/i)).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /Atualizar Cartão/i })).toBeInTheDocument()
  })

  it('renderiza aviso de leitura apenas sem o botao de atualizar cartao quando e outra role', () => {
    vi.mocked(useAuthStore).mockImplementation((selector: any) =>
      selector({ statusAssinatura: 'PAUSADA', role: { nome: 'ACESSO_COMUM' } })
    )

    render(<BannerAssinaturaPausada />)

    expect(screen.getByText(/Assinatura Pausada por Pendência de Pagamento/i)).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: /Atualizar Cartão/i })).not.toBeInTheDocument()
  })
})

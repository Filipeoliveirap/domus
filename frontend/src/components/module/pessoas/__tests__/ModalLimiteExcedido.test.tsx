import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { ModalLimiteExcedido } from '../ModalLimiteExcedido'
import { useAuthStore } from '@/store/authStore'
import { useRotulos } from '@/lib/rotulos/useRotulos'

vi.mock('@/store/authStore', () => ({
  useAuthStore: vi.fn(),
}))

vi.mock('@/lib/rotulos/useRotulos', () => ({
  useRotulos: vi.fn(),
}))

describe('ModalLimiteExcedido', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(useRotulos).mockReturnValue({
      congregacao: { singular: 'Congregação', plural: 'Congregações', genero: 'FEMININO' },
      ministerio: { singular: 'Rede', plural: 'Redes', genero: 'FEMININO' },
      celula: { singular: 'Célula', plural: 'Células', genero: 'FEMININO' },
    } as any)
  })

  it('renderiza nomenclatura dinâmica para "Congregação" com concordância "Suas congregações"', () => {
    vi.mocked(useAuthStore).mockReturnValue(null) // Igreja Mãe

    render(<ModalLimiteExcedido aberto={true} onFechar={vi.fn()} limite={60} />)

    expect(screen.getByText(/Suas congregações atingiram a capacidade máxima de 60 pessoas/i)).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /Fazer Upgrade do Plano/i })).toBeInTheDocument()
  })

  it('renderiza nomenclatura dinâmica para "Rede" com concordância "Sua rede de igrejas"', () => {
    vi.mocked(useAuthStore).mockReturnValue(null)
    vi.mocked(useRotulos).mockReturnValue({
      congregacao: { singular: 'Rede', plural: 'Redes', genero: 'FEMININO' },
    } as any)

    render(<ModalLimiteExcedido aberto={true} onFechar={vi.fn()} limite={300} />)

    expect(screen.getByText(/Sua rede de igrejas atingiram a capacidade máxima de 300 pessoas/i)).toBeInTheDocument()
  })

  it('bloqueia botão de upgrade e exibe aviso restrito quando a igreja é Filha (igrejaMaeId != null)', () => {
    vi.mocked(useAuthStore).mockReturnValue('mae-123') // Igreja Filha vinculada à mãe-123

    render(<ModalLimiteExcedido aberto={true} onFechar={vi.fn()} limite={60} />)

    expect(screen.getByText(/Upgrade restrito à Igreja Contratante/i)).toBeInTheDocument()
    expect(screen.getByText(/Entre em contato com a administração da igreja contratante/i)).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: /Fazer Upgrade do Plano/i })).not.toBeInTheDocument()
  })
})

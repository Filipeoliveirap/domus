import { describe, it, expect, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { BannerAssinaturaPausada } from '../BannerAssinaturaPausada'
import { useAuthStore } from '@/store/authStore'

describe('BannerAssinaturaPausada', () => {
  beforeEach(() => {
    useAuthStore.setState({
      statusAssinatura: null,
      role: null,
    })
  })

  it('nao renderiza nada quando statusAssinatura nao e PAUSADA', () => {
    useAuthStore.setState({ statusAssinatura: 'ATIVA', role: 'ADMIN_IGREJA' })

    const { container } = render(<BannerAssinaturaPausada />)
    expect(container.firstChild).toBeNull()
  })

  it('renderiza aviso de leitura apenas e botao de atualizar cartao quando e ADMIN_IGREJA e statusAssinatura e PAUSADA', () => {
    useAuthStore.setState({ statusAssinatura: 'PAUSADA', role: 'ADMIN_IGREJA' })

    render(<BannerAssinaturaPausada />)

    expect(screen.getByText(/Assinatura Pausada • Modo Somente Leitura/i)).toBeInTheDocument()
    expect(screen.getByText(/Contrate um plano para liberar edições/i)).toBeInTheDocument()
    const link = screen.getByRole('link', { name: /Contratar Plano/i })
    expect(link).toBeInTheDocument()
    expect(link).toHaveAttribute('href', '/planos')
  })

  it('renderiza aviso de leitura apenas sem o botao de atualizar cartao quando e outra role', () => {
    useAuthStore.setState({ statusAssinatura: 'PAUSADA', role: 'ACESSO_COMUM' })

    render(<BannerAssinaturaPausada />)

    expect(screen.getByText(/Assinatura Pausada • Modo Somente Leitura/i)).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: /Contratar Plano/i })).not.toBeInTheDocument()
  })
})

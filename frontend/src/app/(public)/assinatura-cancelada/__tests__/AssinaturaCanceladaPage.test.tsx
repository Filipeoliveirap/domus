import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import AssinaturaCanceladaPage from '../page'

describe('AssinaturaCanceladaPage', () => {
  it('renderiza avisos de assinatura cancelada e botao de reativar', () => {
    render(<AssinaturaCanceladaPage />)

    expect(screen.getByRole('heading', { name: /Assinatura Cancelada/i })).toBeInTheDocument()
    expect(screen.getByText(/A assinatura do plano Domus da sua igreja foi cancelada/i)).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /Reativar Assinatura Agora/i })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /Voltar para o Login/i })).toBeInTheDocument()
  })
})

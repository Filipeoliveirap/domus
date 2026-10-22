import { describe, it, expect, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import TabelaPlanos from '../TabelaPlanos'

describe('TabelaPlanos', () => {
  it('renderiza os 4 planos com precos v2 e limites atualizados', async () => {
    render(<TabelaPlanos />)

    await waitFor(() => {
      expect(screen.getByText('Básico')).toBeDefined()
    })

    expect(screen.getByText('Pro')).toBeDefined()
    expect(screen.getByText('Pro+')).toBeDefined()
    expect(screen.getByText('Enterprise')).toBeDefined()

    // Verifica preços atualizados
    expect(screen.getByText('49,00')).toBeDefined()
    expect(screen.getByText('129,00')).toBeDefined()
    expect(screen.getByText('249,00')).toBeDefined()
    expect(screen.getByText('449,00')).toBeDefined()

    // Verifica limites atualizados
    expect(screen.getByText(/Até 50 pessoas • Sem congregações/)).toBeDefined()
    expect(screen.getByText(/Até 200 pessoas • 2 congregações/)).toBeDefined()
    expect(screen.getByText(/Até 600 pessoas • 5 congregações/)).toBeDefined()
    expect(screen.getByText(/Até Ilimitadas pessoas • 9999 congregações/)).toBeDefined()
  })
})

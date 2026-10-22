import { describe, it, expect, vi } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { CompartilharConviteCongregacao } from '../CompartilharConviteCongregacao'

describe('CompartilharConviteCongregacao', () => {
  it('renderiza o link de convite e botao de copiar e whatsapp', () => {
    render(<CompartilharConviteCongregacao codigo="DOMUS-TEST12" nomeMatriz="Igreja Central" />)

    expect(screen.getByDisplayValue(/DOMUS-TEST12/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /Copiar link/i })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /WhatsApp/i })).toHaveAttribute('href', expect.stringContaining('wa.me'))
  })

  it('copia o link ao clicar no botao de copiar', async () => {
    const writeTextMock = vi.fn().mockResolvedValue(undefined)
    Object.assign(navigator, {
      clipboard: {
        writeText: writeTextMock,
      },
    })

    render(<CompartilharConviteCongregacao codigo="DOMUS-TEST12" nomeMatriz="Igreja Central" />)

    const btnCopiar = screen.getByRole('button', { name: /Copiar link/i })
    fireEvent.click(btnCopiar)

    expect(writeTextMock).toHaveBeenCalledWith(expect.stringContaining('DOMUS-TEST12'))
  })
})

import { describe, it, expect, vi } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { Passo3Checkout } from '../Passo3Checkout'

describe('Passo3Checkout', () => {
  it('renderiza os campos de cartao de credito e aviso de 14 dias gratis para igreja matriz', () => {
    render(
      <Passo3Checkout
        planoId="PRO"
        codigoConvite={null}
        matrizNome={null}
        isLoading={false}
        erroGeral={null}
        onSubmit={vi.fn()}
        onVoltar={vi.fn()}
      />
    )

    expect(screen.getByText(/Plano Pro/i)).toBeInTheDocument()
    expect(screen.getByText('14 dias grátis')).toBeInTheDocument()
    expect(screen.getByLabelText(/NÚMERO DO CARTÃO/i)).toBeInTheDocument()
    expect(screen.getByLabelText(/NOME IMPRESSO NO CARTÃO/i)).toBeInTheDocument()
    expect(screen.getByLabelText(/VALIDADE/i)).toBeInTheDocument()
    expect(screen.getByLabelText(/CVV/i)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /Começar 14 dias grátis/i })).toBeInTheDocument()
  })

  it('nao renderiza campos de cartao e exibe vinculo com matriz para igreja filha com codigo', () => {
    render(
      <Passo3Checkout
        planoId="PRO"
        codigoConvite="DOMUS-TEST12"
        matrizNome="Igreja Sede Central"
        isLoading={false}
        erroGeral={null}
        onSubmit={vi.fn()}
        onVoltar={vi.fn()}
      />
    )

    expect(screen.getByText(/Plano Coberto pela Matriz/i)).toBeInTheDocument()
    expect(screen.getByText(/Isento de cobrança/i)).toBeInTheDocument()
    expect(screen.getByText(/Igreja Sede Central/i)).toBeInTheDocument()
    expect(screen.queryByLabelText(/NÚMERO DO CARTÃO/i)).not.toBeInTheDocument()
    expect(screen.queryByLabelText(/CVV/i)).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: /Concluir cadastro gratuitamente/i })).toBeInTheDocument()
  })

  it('aciona onSubmit ao submeter igreja filha sem exigir cartao', async () => {
    const onSubmitMock = vi.fn().mockResolvedValue(undefined)
    render(
      <Passo3Checkout
        planoId="PRO"
        codigoConvite="DOMUS-TEST12"
        matrizNome="Igreja Sede Central"
        isLoading={false}
        erroGeral={null}
        onSubmit={onSubmitMock}
        onVoltar={vi.fn()}
      />
    )

    const btnSubmit = screen.getByRole('button', { name: /Concluir cadastro gratuitamente/i })
    fireEvent.click(btnSubmit)

    await waitFor(() => {
      expect(onSubmitMock).toHaveBeenCalledTimes(1)
    })
  })

  it('aciona onVoltar ao clicar no botao de voltar', () => {
    const onVoltarMock = vi.fn()
    render(
      <Passo3Checkout
        planoId="PRO"
        codigoConvite={null}
        matrizNome={null}
        isLoading={false}
        erroGeral={null}
        onSubmit={vi.fn()}
        onVoltar={onVoltarMock}
      />
    )

    const btnVoltar = screen.getByRole('button', { name: /Voltar/i })
    fireEvent.click(btnVoltar)

    expect(onVoltarMock).toHaveBeenCalledTimes(1)
  })

  it('exibe mensagem de erro quando erroGeral estiver preenchido', () => {
    render(
      <Passo3Checkout
        planoId="PRO"
        codigoConvite={null}
        matrizNome={null}
        isLoading={false}
        erroGeral="Cartão recusado pelo emissor"
        onSubmit={vi.fn()}
        onVoltar={vi.fn()}
      />
    )

    expect(screen.getByText(/Cartão recusado pelo emissor/i)).toBeInTheDocument()
  })
})

// Testes do schema Zod do modal de Pagamento.
// Validação do `ModalPagamento` no submit.
//
// Onde fica a regra equivalente no domínio:
//   - backend/api/src/main/java/.../contapagar/PagamentoContaRequest.java

import { describe, it, expect } from 'vitest'
import { pagamentoSchema } from '@/lib/financeiro/contas-a-pagar/schemas'

const basePagamento = {
  valor: '100.50',
  data: '2027-10-15',
  meioPagamento: 'PIX' as const,
  observacao: '',
}

describe('pagamentoSchema', () => {
  it('aceita_pagamento_minimo_valido', () => {
    const r = pagamentoSchema.safeParse(basePagamento)
    expect(r.success).toBe(true)
  })

  it('rejeita_valor_vazio', () => {
    expect(pagamentoSchema.safeParse({ ...basePagamento, valor: '' }).success).toBe(false)
  })

  it('rejeita_valor_zero_ou_negativo', () => {
    expect(pagamentoSchema.safeParse({ ...basePagamento, valor: '0' }).success).toBe(false)
    expect(pagamentoSchema.safeParse({ ...basePagamento, valor: '-1' }).success).toBe(false)
  })

  it('rejeita_data_fora_do_formato_iso', () => {
    expect(pagamentoSchema.safeParse({ ...basePagamento, data: '15/10/2027' }).success).toBe(false)
    expect(pagamentoSchema.safeParse({ ...basePagamento, data: '' }).success).toBe(false)
  })

  it('aceita_todas_as_formas_de_pagamento_canonicas', () => {
    const formas = [
      'PIX',
      'DINHEIRO',
      'TRANSFERENCIA',
      'BOLETO',
      'CARTAO_CREDITO',
      'CARTAO_DEBITO',
      'OUTRO',
    ] as const
    for (const forma of formas) {
      expect(pagamentoSchema.safeParse({ ...basePagamento, meioPagamento: forma }).success).toBe(true)
    }
  })

  it('rejeita_forma_de_pagamento_desconhecida', () => {
    expect(
      pagamentoSchema.safeParse({ ...basePagamento, meioPagamento: 'CRIPTOMOEDA' }).success,
    ).toBe(false)
  })

  it('aceita_observacao_vazia_opcional', () => {
    expect(pagamentoSchema.safeParse({ ...basePagamento, observacao: '' }).success).toBe(true)
  })
})

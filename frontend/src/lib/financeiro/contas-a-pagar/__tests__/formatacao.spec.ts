// Testes das funções de formatação monetária usadas pela feature
// `/financeiro/contas-a-pagar` (Resumo, ModalContaForm, ModalPagamento).
//
// Cobertura: forma do que o usuário vê — formato BR, tratamento de vazio/null/NaN.

import { describe, it, expect } from 'vitest'
import {
  formatarMoeda,
  formatarValorDigitado,
} from '@/lib/formats/financeiro/movimentacaoFormat'

describe('formatarMoeda', () => {
  it('formata_numero_em_reais_br_com_simbolo_e_decimais', () => {
    const out = formatarMoeda(1234.56)
    // pt-BR: R$ 1.234,56
    expect(out).toMatch(/R\$/)
    expect(out).toContain('1.234')
    expect(out).toContain('56')
  })

  it('aceita_string_decimal_e_converte', () => {
    const out = formatarMoeda('99.90')
    expect(out).toContain('99')
    expect(out).toContain('90')
  })

  it('devolve_traco_quando_valor_e_null', () => {
    expect(formatarMoeda(null)).toBe('—')
  })

  it('devolve_traco_quando_valor_e_undefined', () => {
    expect(formatarMoeda(undefined)).toBe('—')
  })

  it('devolve_traco_quando_string_nao_e_numero', () => {
    expect(formatarMoeda('abc')).toBe('—')
  })

  it('formata_zero_como_zero_reais', () => {
    expect(formatarMoeda(0)).toContain('0')
  })
})

describe('formatarValorDigitado', () => {
  it('devolve_string_vazia_quando_entrada_vazia', () => {
    expect(formatarValorDigitado('')).toBe('')
  })

  it('devolve_string_vazia_quando_entrada_invalida', () => {
    expect(formatarValorDigitado('xxx')).toBe('')
  })

  it('formata_numero_valido_em_reais_br', () => {
    const out = formatarValorDigitado('1500.00')
    expect(out).toMatch(/R\$/)
    expect(out).toContain('1.500')
  })
})

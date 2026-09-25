// Testes do schema Zod de cadastro/edição de Conta a Pagar.
// Validam as regras que o `ModalContaForm` aplica no submit.
//
// Onde fica a regra equivalente no domínio:
//   - backend/api/src/main/java/.../contapagar/ContaPagarRequest.java
//   - regras de negócio no ContaPagarService

import { describe, it, expect } from 'vitest'
import { contaSchema, recorrenciaSchema, beneficiarioSchema } from '@/lib/financeiro/contas-a-pagar/schemas'

const baseConta = {
  descricao: 'Conta de luz',
  valor: '150.00',
  vencimento: '2027-12-31',
  beneficiario: { pessoaId: 'uuid-1', texto: null },
  categoriaId: 'cat-1',
  recorrencia: { tipo: 'SEM_RECORRENCIA' as const },
  observacoes: '',
  anexoId: null,
}

describe('contaSchema', () => {
  it('aceita_quando_todos_campos_obrigatorios_preenchidos', () => {
    const result = contaSchema.safeParse(baseConta)
    expect(result.success).toBe(true)
  })

  it('rejeita_quando_valor_esta_vazio', () => {
    const result = contaSchema.safeParse({ ...baseConta, valor: '' })
    expect(result.success).toBe(false)
    if (!result.success) {
      expect(result.error.issues.some((i) => i.path.includes('valor'))).toBe(true)
    }
  })

  it('rejeita_quando_valor_e_zero_ou_negativo', () => {
    expect(contaSchema.safeParse({ ...baseConta, valor: '0' }).success).toBe(false)
    expect(contaSchema.safeParse({ ...baseConta, valor: '-10' }).success).toBe(false)
  })

  it('aceita_descricao_vazia_opcional', () => {
    // descricao é .optional(), vazio é aceitável
    const result = contaSchema.safeParse({ ...baseConta, descricao: '' })
    expect(result.success).toBe(true)
  })

  it('rejeita_quando_vencimento_nao_segue_formato_iso', () => {
    expect(contaSchema.safeParse({ ...baseConta, vencimento: '31/12/2027' }).success).toBe(false)
    expect(contaSchema.safeParse({ ...baseConta, vencimento: 'ontem' }).success).toBe(false)
  })

  it('rejeita_quando_beneficiario_nao_tem_pessoa_nem_texto', () => {
    const result = contaSchema.safeParse({
      ...baseConta,
      beneficiario: { pessoaId: null, texto: null },
    })
    expect(result.success).toBe(false)
  })

  it('rejeita_quando_beneficiario_so_tem_texto_em_branco', () => {
    const result = contaSchema.safeParse({
      ...baseConta,
      beneficiario: { pessoaId: null, texto: '   ' },
    })
    expect(result.success).toBe(false)
  })

  it('aceita_beneficiario_com_texto_livre_sem_pessoa', () => {
    const result = contaSchema.safeParse({
      ...baseConta,
      beneficiario: { pessoaId: null, texto: 'Fornecedor avulso' },
    })
    expect(result.success).toBe(true)
  })
})

describe('recorrenciaSchema', () => {
  it('aceita_tipos_canonicos', () => {
    expect(recorrenciaSchema.safeParse({ tipo: 'MENSAL' }).success).toBe(true)
    expect(recorrenciaSchema.safeParse({ tipo: 'TRIMESTRAL' }).success).toBe(true)
    expect(recorrenciaSchema.safeParse({ tipo: 'SEM_RECORRENCIA' }).success).toBe(true)
  })

  it('rejeita_tipo_desconhecido', () => {
    expect(recorrenciaSchema.safeParse({ tipo: 'DIARIO' }).success).toBe(false)
  })

  it('aceita_parcela_atual_e_total_quando_definidos', () => {
    const r = recorrenciaSchema.safeParse({ tipo: 'MENSAL', parcelaAtual: 1, totalParcelas: 12 })
    expect(r.success).toBe(true)
  })
})

describe('beneficiarioSchema', () => {
  it('aceita_apenas_pessoa_id', () => {
    expect(beneficiarioSchema.safeParse({ pessoaId: 'x', texto: null }).success).toBe(true)
  })

  it('aceita_apenas_texto', () => {
    expect(beneficiarioSchema.safeParse({ pessoaId: null, texto: 'João' }).success).toBe(true)
  })

  it('rejeita_os_dois_nulos', () => {
    expect(beneficiarioSchema.safeParse({ pessoaId: null, texto: null }).success).toBe(false)
  })

  it('rejeita_texto_vazio', () => {
    expect(beneficiarioSchema.safeParse({ pessoaId: null, texto: '' }).success).toBe(false)
  })
})

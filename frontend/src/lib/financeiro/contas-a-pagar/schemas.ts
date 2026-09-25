import { z } from 'zod'

// ─── Recorrência ─────────────────────────────────────────────────────────────

export const recorrenciaSchema = z.object({
  tipo: z.enum([
    'SEM_RECORRENCIA',
    'SEMANAL',
    'QUINZENAL',
    'MENSAL',
    'BIMESTRAL',
    'TRIMESTRAL',
    'SEMESTRAL',
    'ANUAL',
  ]),
  parcelaAtual: z.number().int().positive().optional(),
  totalParcelas: z.number().int().positive().optional(),
})

// ─── Beneficiário ────────────────────────────────────────────────────────────

export const beneficiarioSchema = z
  .object({
    pessoaId: z.string().nullable(),
    texto: z.string().max(255).nullable(),
  })
  .refine(
    (b) => b.pessoaId !== null || (b.texto !== null && b.texto.trim().length > 0),
    { message: 'Selecione uma pessoa ou digite um nome.', path: ['texto'] },
  )

// ─── Conta (formulário) ──────────────────────────────────────────────────────

export const contaSchema = z.object({
  descricao: z.string().max(255).optional(),
  valor: z
    .string()
    .min(1, 'Valor é obrigatório')
    .refine((v) => parseFloat(v) > 0, 'Valor deve ser maior que zero'),
  vencimento: z
    .string()
    .regex(/^\d{4}-\d{2}-\d{2}$/, 'Data de vencimento inválida'),
  beneficiario: beneficiarioSchema,
  categoriaId: z.string().optional().nullable(),
  recorrencia: recorrenciaSchema,
  observacoes: z.string().max(1000).optional(),
  anexoId: z.string().nullable().optional(),
})

export type ContaFormValues = z.infer<typeof contaSchema>

// ─── Pagamento ──────────────────────────────────────────────────────────────

export const pagamentoSchema = z.object({
  valor: z
    .string()
    .min(1, 'Valor é obrigatório')
    .refine((v) => parseFloat(v) > 0, 'Valor deve ser maior que zero'),
  data: z.string().regex(/^\d{4}-\d{2}-\d{2}$/, 'Data inválida'),
  meioPagamento: z.enum([
    'PIX',
    'DINHEIRO',
    'TRANSFERENCIA',
    'BOLETO',
    'CARTAO_CREDITO',
    'CARTAO_DEBITO',
    'OUTRO',
  ]),
  observacao: z.string().max(500).optional(),
})

export type PagamentoFormValues = z.infer<typeof pagamentoSchema>

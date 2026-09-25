import type { PagedResponse } from '@/types/pagedResponse.type'

// ─── Enums ──────────────────────────────────────────────────────────────────

export type StatusConta = 'EM_ABERTO' | 'PAGA' | 'PARCIAL'
export type FormaPagamento =
  | 'PIX'
  | 'DINHEIRO'
  | 'TRANSFERENCIA'
  | 'BOLETO'
  | 'CARTAO_CREDITO'
  | 'CARTAO_DEBITO'
  | 'OUTRO'
export type RecorrenciaTipo =
  | 'SEM_RECORRENCIA'
  | 'SEMANAL'
  | 'QUINZENAL'
  | 'MENSAL'
  | 'BIMESTRAL'
  | 'TRIMESTRAL'
  | 'SEMESTRAL'
  | 'ANUAL'
export type EscopoEdicaoSerie = 'ESTA' | 'ESTA_E_SEGUINTES' | 'SERIE'

// ─── Shared ─────────────────────────────────────────────────────────────────

export type BeneficiarioValue = { pessoaId: string | null; texto: string | null }

export type BeneficiarioResponse = {
  pessoaId: string | null
  pessoaNome: string | null
  texto: string | null
  pessoaRemovida: boolean
}

export type RecorrenciaResponse = {
  tipo: RecorrenciaTipo
  parcelaAtual: number | null
  totalParcelas: number | null
}

export type Recorrencia = {
  tipo: RecorrenciaTipo
  parcelaAtual?: number
  totalParcelas?: number
}

// ─── Anexo ──────────────────────────────────────────────────────────────────

export interface AnexoResponse {
  id: string
  tipo: string
  bytes: number
  nomeOriginal: string
  url: string
}

export interface AnexoUploadResponse {
  id: string
  tipo: string
  bytes: number
  nomeOriginal: string
  url: string
}

// ─── Pagamento ─────────────────────────────────────────────────────────────

export interface PagamentoResponse {
  id: string
  valorPago: string
  data: string
  forma: FormaPagamento
  anexoId?: string
  movimentacaoId: string
  estornadoPorTexto?: string
  estornadoEm?: string
}

export interface RegistrarPagamentoRequest {
  valor: string
  data: string
  meioPagamento: FormaPagamento
  observacao?: string
}

export interface PagamentoRequest {
  valor: string
  data: string
  forma: FormaPagamento
  jurosAcrescimos?: string
  desconto?: string
  anexoId?: string | null
}

// ─── Conta ──────────────────────────────────────────────────────────────────

export interface ContaResponse {
  id: string
  categoriaId: string
  categoriaNome: string
  categoriaCor?: string
  beneficiario: BeneficiarioResponse
  descricao?: string
  valor: string
  valorPago: string
  vencimento: string
  diasParaVencimento: number | null
  status: StatusConta
  competencia?: string
  documentoNumero?: string
  observacoes?: string
  anexoId?: string
  anexo?: AnexoUploadResponse
  recorrencia?: RecorrenciaResponse
  podeReceberPagamento: boolean
  divergeDaSerie: boolean
  criadoPorNome?: string
  criadoEm: string
  atualizadoEm: string
  pagamentos?: PagamentoResponse[]
}

// ─── Resumo ────────────────────────────────────────────────────────────────

export interface ResumoResponse {
  venceHoje: string
  aVencerNoMes: string
  atrasadas: string
  pagasNoMes: string
}

// ─── Filtros ───────────────────────────────────────────────────────────────

export interface ListarContasFiltros {
  status?: StatusConta | ''
  beneficiario?: string
  competencia?: string
  vencimentoAte?: string
  page?: number
  size?: number
}

// ─── Projecao (series) ───────────────────────────────────────────────────────

export interface ProjecaoItem {
  ocorrenciaId: string | null
  vencimento: string
  valor: string
  status: StatusConta | null
  materializada: boolean
}

// ─── Request payloads ───────────────────────────────────────────────────────

export interface BeneficiarioInput {
  pessoaId: string | null
  texto: string | null
}

export interface RecorrenciaInput {
  tipo: RecorrenciaTipo
  parcelaAtual?: number
  totalParcelas?: number
}

export interface ContaRequest {
  descricao?: string | null
  valor: string
  vencimento: string
  beneficiario: BeneficiarioInput
  categoriaId?: string | null
  competencia?: string | null
  documentoNumero?: string | null
  observacoes?: string | null
  anexoId?: string | null
  recorrencia?: RecorrenciaInput | null
}

// ─── Paged response ─────────────────────────────────────────────────────────

export type ContasPagedResponse = PagedResponse<ContaResponse>

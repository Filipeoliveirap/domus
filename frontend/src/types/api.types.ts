import type { Impedimento } from '@/types/inscricao.type'

export interface ApiError {
  status: number;
  error: string;
  message: string;
  timestamp: string;
  campos?: Record<string, string>;
  /** Presente só no 422 de NAO_ELEGIVEL (`ErrorResponse.ofElegibilidade`). */
  impedimentos?: Impedimento[];
}

/** Meio de pagamento de uma cobrança de evento pago. */
export type MeioPagamento = 'PIX' | 'CARTAO'

/** Uma opção de pagamento calculada pelo backend (gross-up da taxa do Mercado Pago já
 *  embutido em `valorTotal`). PIX sempre vem como 1 opção; CARTAO vem uma por faixa de
 *  parcelas (1..maxParcelas) quando o evento aceita cartão. */
export interface OpcaoPagamento {
  meio: MeioPagamento
  parcelas: number
  /** Quanto o pagador paga no total nessa opção (valor do evento + taxa repassada). */
  valorTotal: number
  /** `valorTotal / parcelas`. */
  valorParcela: number
  /** Taxa do Mercado Pago repassada ao pagador nessa opção. */
  taxa: number
}

/** Resposta de `GET /cobrancas/{id}/opcoes-pagamento` e `POST /eventos/simular-pagamento`. */
export interface OpcoesPagamentoResponse {
  /** Quanto a igreja recebe — o mesmo em qualquer opção. */
  valorEvento: number
  opcoes: OpcaoPagamento[]
}

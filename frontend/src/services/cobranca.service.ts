import { api } from '@/lib/api'
import { Endpoints } from '@/lib/endpoints'

export type StatusCobranca = 'PENDENTE' | 'PAGO' | 'EXPIRADO' | 'CANCELADO' | 'REEMBOLSADO'

/** Espelha `CobrancaPublicaDTO` (backend) — rota pública, sem sessão. */
export interface CobrancaPublica {
  id: string
  tituloEvento: string
  nomePagador: string
  valor: number
  status: StatusCobranca
  expiraEm: string
}

/** Payload que o Payment Brick devolve em `onSubmit({ formData })`, repassado quase igual
 *  pro backend (`token`/`installments` vêm nulos quando o meio escolhido é PIX). */
export interface PagarCobrancaRequest {
  token: string | null
  paymentMethodId: string
  installments: number | null
  payerEmail: string
}

export interface PagarCobrancaResponse {
  mpPaymentId: string
}

export const cobrancaService = {
  buscarPorToken: (token: string): Promise<CobrancaPublica> =>
    api.get<CobrancaPublica>(Endpoints.cobrancas.BUSCAR_POR_TOKEN(token)).then((res) => res.data),

  /** Só INICIA o pagamento no Mercado Pago — a confirmação definitiva (marcar como PAGO)
   *  chega depois, assíncrona, pelo webhook (Task 10). Sem autenticação: quem chama já
   *  possui o `id` (devolvido na inscrição do titular, ou aqui em `buscarPorToken` pro
   *  link público) — mesma garantia de posse que já vale pro token do link. */
  pagar: (cobrancaId: string, dados: PagarCobrancaRequest): Promise<PagarCobrancaResponse> =>
    api.post<PagarCobrancaResponse>(Endpoints.cobrancas.PAGAR(cobrancaId), dados).then((res) => res.data),
}

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

/** Espelha `CobrancaCheckoutDTO` (backend) — usado pela página de checkout dedicada
 *  (`/eventos/{eventoId}/pagamento/{cobrancaId}`), que precisa do contexto do evento
 *  (título, data) além do que `CobrancaPublica` já tinha. */
export interface CobrancaCheckout {
  id: string
  eventoId: string
  tituloEvento: string
  inicioEmEvento: string
  nomePagador: string
  /** Nulo quando o pagador é acompanhante sem cadastro (não tem e-mail). Pré-preenche o
   *  Payment Brick pra ele não pedir e-mail de novo no Pix. */
  emailPagador: string | null
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
  /** `formData.issuer_id` do Brick — nulo pra Pix. Sem ele, o Mercado Pago falha o cálculo
   *  de parcelamento/preço pra alguns bancos emissores (`error_pricing`, código 10107)
   *  mesmo com token/cartão válidos. */
  issuerId: string | null
}

export interface PagarCobrancaResponse {
  mpPaymentId: string
  /** Status bruto do Mercado Pago (`approved`, `pending`, `rejected`, ...). */
  status: string
  /** Só vem preenchido quando o meio escolhido foi Pix. */
  qrCode: string | null
  /** Só vem preenchido quando o meio escolhido foi Pix — imagem do QR em base64. */
  qrCodeBase64: string | null
}

export interface StatusCobrancaResponse {
  status: StatusCobranca
}

export const cobrancaService = {
  buscarPorToken: (token: string): Promise<CobrancaPublica> =>
    api.get<CobrancaPublica>(Endpoints.cobrancas.BUSCAR_POR_TOKEN(token)).then((res) => res.data),

  buscarPorId: (id: string): Promise<CobrancaCheckout> =>
    api.get<CobrancaCheckout>(Endpoints.cobrancas.BUSCAR_POR_ID(id)).then((res) => res.data),

  /** Só INICIA o pagamento no Mercado Pago — a confirmação definitiva (marcar como PAGO)
   *  chega depois, assíncrona, pelo webhook (Task 10). Sem autenticação: quem chama já
   *  possui o `id` (devolvido na inscrição do titular, ou aqui em `buscarPorToken` pro
   *  link público) — mesma garantia de posse que já vale pro token do link. */
  pagar: (cobrancaId: string, dados: PagarCobrancaRequest): Promise<PagarCobrancaResponse> =>
    api.post<PagarCobrancaResponse>(Endpoints.cobrancas.PAGAR(cobrancaId), dados).then((res) => res.data),

  /** Poll usado enquanto o QR do Pix está na tela, esperando o webhook confirmar. */
  status: (cobrancaId: string): Promise<StatusCobrancaResponse> =>
    api.get<StatusCobrancaResponse>(Endpoints.cobrancas.STATUS(cobrancaId)).then((res) => res.data),
}

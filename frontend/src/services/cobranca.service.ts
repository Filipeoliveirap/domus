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
  /** Já existe uma tentativa de pagamento em voo (mpPaymentId gravado, esperando o
   *  webhook confirmar) — usado pra retomar a tela de "confirmando" após um reload
   *  em vez de mostrar o formulário de pagamento de novo. */
  pagamentoEmAndamento: boolean
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
  valor: number
  status: StatusCobranca
  expiraEm: string
  /** Mesma lógica de {@link CobrancaPublica.pagamentoEmAndamento}. */
  pagamentoEmAndamento: boolean
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
  /** Motivo específico da recusa (ex.: `cc_rejected_insufficient_amount`) — usado pra
   *  mostrar uma mensagem certeira em vez de "cartão recusado" genérico. Nulo fora de
   *  `status === 'rejected'`. */
  statusDetail: string | null
  /** Só vem preenchido quando o meio escolhido foi Pix. */
  qrCode: string | null
  /** Só vem preenchido quando o meio escolhido foi Pix — imagem do QR em base64. */
  qrCodeBase64: string | null
  /** Validade real deste Pix específico (30min) — NUNCA o prazo da cobrança inteira (que
   *  pode chegar a 48h pra link compartilhado). Nulo fora de Pix. */
  expiraEmPix: string | null
}

export interface StatusCobrancaResponse {
  status: StatusCobranca
}

/** Espelha `CobrancaController.PixResponse` (backend). Os dois campos vêm nulos quando o
 *  pagamento em andamento acabou sendo cartão (não Pix) — cartão nunca tem QR. */
export interface PixResponse {
  qrCode: string | null
  qrCodeBase64: string | null
  /** Validade real deste Pix específico — ver {@link PagarCobrancaResponse.expiraEmPix}. */
  expiraEm: string | null
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

  /** Recupera o QR/copia-e-cola de um pagamento Pix já criado — usado pra retomar a tela
   *  do Pix depois de um reload no meio do pagamento (o QR só vinha na resposta de `pagar`,
   *  que não pode ser chamado de novo). 404 se não há tentativa de pagamento em andamento. */
  pix: (cobrancaId: string): Promise<PixResponse> =>
    api.get<PixResponse>(Endpoints.cobrancas.PIX(cobrancaId)).then((res) => res.data),

  /** "Cancelar inscrição" do e-mail de lembrete de pagamento pendente — sem sessão, mesma
   *  garantia de posse do resto do módulo (o `id` já prova posse). Só tem efeito enquanto a
   *  inscrição ainda está aguardando pagamento; um link velho não faz nada (backend recusa). */
  cancelarInscricao: (cobrancaId: string): Promise<void> =>
    api.post(Endpoints.cobrancas.CANCELAR_INSCRICAO(cobrancaId)).then(() => undefined),

  /** "Gerar novo QR code" / "Pagar com outro método" — libera uma tentativa de pagamento
   *  presa (Pix escaneado mas nunca pago) pra tentar de novo, com qualquer meio. */
  reiniciar: (cobrancaId: string): Promise<void> =>
    api.post(Endpoints.cobrancas.REINICIAR(cobrancaId)).then(() => undefined),

  /** Retry manual da tag "Estorno pendente" (2026-08-27) — ao contrário do resto deste
   *  service, EXIGE sessão de ADMIN/LÍDER (é ação de gestão, não do próprio pagador). */
  tentarEstornoNovamente: (cobrancaId: string): Promise<void> =>
    api.post(Endpoints.cobrancas.TENTAR_ESTORNO_NOVAMENTE(cobrancaId)).then(() => undefined),
}

package com.domus.api.modules.pagamento.cobranca.DTOs;

import java.time.Instant;

/**
 * Resposta de {@code POST /cobrancas/{id}/pagar}. {@code mpPaymentId} identifica o
 * pagamento criado no Mercado Pago — a confirmação definitiva (marcar a
 * {@code CobrancaEvento} como PAGO) NÃO acontece aqui, e sim de forma assíncrona pelo
 * webhook (Task 10). Este endpoint só inicia o pagamento.
 *
 * <p>{@code qrCode}/{@code qrCodeBase64} só vêm preenchidos quando o meio escolhido foi
 * Pix — nesse caso o pagamento nasce {@code pending} e o front precisa mostrar o código
 * pra pessoa escanear/colar, em vez de fechar o checkout como faz com cartão (que já
 * resolve aprovado/recusado na hora). {@code status} é o status bruto do Mercado Pago
 * ({@code approved}, {@code pending}, {@code rejected}, ...).</p>
 *
 * <p>{@code statusDetail} é o motivo específico do Mercado Pago (ex.:
 * {@code cc_rejected_insufficient_amount}, {@code cc_rejected_call_for_authorize},
 * {@code cc_rejected_bad_filled_security_code}) — só o {@code status} bruto não distingue
 * "sem saldo" de "CVV errado" de "recusa geral". O front usa isso pra mostrar uma mensagem
 * específica em vez do genérico "cartão recusado" pra todo caso de recusa (ver
 * {@code PaymentBrickCheckout.tsx}, mapa {@code MENSAGENS_RECUSA}).</p>
 */
/**
 * <p>{@code expiraEmPix} é a validade real deste QR Pix específico — sempre bem mais curta
 * (30 min, ver {@code MercadoPagoApi.MINUTOS_EXPIRACAO_PIX}) que o prazo geral da
 * {@code CobrancaEvento} (que pode chegar a 48h pra link compartilhado). Achado ao vivo
 * (2026-08-27): usar o prazo da cobrança como se fosse a validade do Pix mostrava um
 * contador sem sentido (ex.: quase 48h) pra quem pagava por um link de lembrete. Nulo fora
 * de Pix (cartão não tem essa noção).</p>
 */
public record PagarCobrancaResponse(String mpPaymentId, String status, String statusDetail, String qrCode,
                                     String qrCodeBase64, Instant expiraEmPix) {}
